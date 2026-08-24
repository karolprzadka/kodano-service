# Kodano 

Serwis przyjmuje zdarzenia zamówień z Magento (pojedyncze webhooki) oraz dobowe paczki sprzedaży
z soczewkomatów, i sprowadza oba strumienie do jednego, niepodwajalnego obrazu w PostgreSQL.

## Stack

| | |
|---|---|
| Java | 21 (Microsoft Build of OpenJDK 21.0.11) |
| Spring Boot | 3.4.5 |
| Baza | PostgreSQL 17, migracje Flyway |
| Dostęp do danych | Spring Data JPA + `NamedParameterJdbcTemplate` |
| Kontrakt | OpenAPI 3 (`src/main/resources/openapi/inbox-api.yaml`), api-first |
| Biblioteki | Vavr 0.10.4, MapStruct 1.6.3, Lombok |
| Testy | JUnit 5 + Testcontainers (prawdziwy PostgreSQL 17), Awaitility |

Bez brokera — kolejka wejściowa i DLQ żyją w Postgresie (`FOR UPDATE SKIP LOCKED`), zgodnie z sekcją 4 zadania.

## Uruchomienie

Wymagania: Docker (z uruchomionym demonem) oraz — do budowania i testów lokalnie — Java 21 i Maven 3.9+.
Świadomie nie dołączam maven wrappera; projekt buduje się zwykłym `mvn`.

```bash
docker compose up --build
```

Podnosi PostgreSQL 17 i aplikację (aplikacja czeka na `service_healthy` bazy, Flyway zakłada schemat i dane
początkowe). API: `http://localhost:8080/api/v1`, stan: `http://localhost:8080/actuator/health`.

Testy:

```bash
mvn test
```

Testcontainers startuje własny kontener `postgres:17-alpine`. W `pom.xml` ustawiam surefire'owi
`api.version=1.44`, bo docker-java domyślnie negocjuje API Dockera w wersji 1.32, a silniki od Dockera 26
w górę odrzucają je z HTTP 400 (`min API 1.40`). Bez tego testy nie wystartują na świeżym Dockerze.

Tokeny nadawców (seed, w bazie trzymane jako SHA-256):

| źródło | token |
|---|---|
| magento | `magento-dev-token` |
| vending | `vending-dev-token` |

Token idzie w nagłówku `X-Api-Token`. Przykładowe wywołania: [`requests.http`](requests.http).

Plik OpenAPI szkicowałem w edytorze online (onlinedevtools.dev/tools/openapi-generator) i dalej pielęgnowałem ręcznie;
kontrolery implementują interfejsy generowane z tego pliku, więc rozjazd kontraktu z kodem wywala się na kompilacji.

## API

| metoda | ścieżka | opis |
|---|---|---|
| POST | `/api/v1/inbox/magento/events` | przyjęcie zdarzenia zamówienia, 202 + `ACCEPTED`/`DUPLICATE` |
| POST | `/api/v1/inbox/vending/batches` | przyjęcie paczki, 202 + wynik **per linia** |
| GET | `/api/v1/orders/{orderId}` | projekcja zamówienia |
| GET | `/api/v1/inbox/reconciliation` | licznik, suma kontrolna i brakujące numery per automat |
| GET | `/api/v1/inbox/dead-letter` | zaparkowane komunikaty |
| POST | `/api/v1/inbox/dead-letter/{id}/retry` | ponowne skierowanie do przetworzenia |

### 1. Exactly-once egzekwowane przez bazę

Przyjęcie komunikatu to jeden `INSERT ... ON CONFLICT (source_code, external_id) DO NOTHING`.
O wyniku decyduje liczba wstawionych wierszy: 1 → `ACCEPTED`, 0 → `DUPLICATE`.

Nie ma tu wzorca „SELECT czy istnieje, potem INSERT", bo między odczytem a zapisem mieści się cała
równoległa dostawa — dwa wątki zobaczyłyby „nie ma" i oba wstawiły. Jednoznaczności pilnuje
ograniczenie `UNIQUE(source_code, external_id)`, czyli jeden indeks w bazie; przy 20 równoległych
kopiach dokładnie jedna wygrywa, a pozostałe dostają ten sam wynik co przy zwykłej powtórce.

Klucze zewnętrzne: `eventId` dla magento, `deviceId:seq` dla vending. Każda linia paczki jest osobnym
rekordem inboxu, więc `UNIQUE(device_id, seq)` działa już na wejściu, a nie dopiero przy księgowaniu.
Idempotencja całej paczki dodatkowo przez `UNIQUE(device_id, batch_external_id)` w `vending_batches`.

### 2. Kolejność per agregat przy wielu workerach

Worker pobiera komunikat zapytaniem:

```sql
select ... from inbox_messages
where status = 'pending'
  and next_attempt_at <= now()
  and pg_try_advisory_xact_lock(hashtext(aggregate_type || ':' || aggregate_id))
order by received_at
limit 1
for update skip locked
```

Dwie blokady robią dwie różne rzeczy:

* `FOR UPDATE SKIP LOCKED` — workery nie biją się o ten sam **wiersz**; zajęty wiersz jest pomijany, nie oczekiwany.
* `pg_try_advisory_xact_lock` na kluczu agregatu — zdarzenia jednego zamówienia (albo jednego automatu)
  nie mogą być przetwarzane równolegle. Jeśli inny worker trzyma agregat, wiersz po prostu nie zostanie
  wybrany i poczeka na następny cykl. Lock jest transakcyjny, więc zwalnia się sam przy commicie
  lub rollbacku — nie ma czego sprzątać po awarii workera.

Serializuje więc **baza**, nie kod. Sama kolejność dostarczenia i tak nie wpływa na wynik (patrz niżej),
ale bez advisory locka dwa workery mogłyby jednocześnie czytać historię zamówienia i zapisywać dwie
konkurencyjne wersje projekcji.

### 3. Wykrywanie luk odporne na duplikaty i paczki nie po kolei

Sprzedaż automatu ląduje w `vending_sales` z `UNIQUE(device_id, seq)` i wstawką `ON CONFLICT DO NOTHING`.
Rekoncyliacja to jedno zapytanie zbiorcze z funkcją okna — żadnej pętli po rekordach w Javie:

```sql
with received as (
   select seq, lag(seq) over (order by seq) as previous_seq
   from vending_sales where device_id = :deviceId
)
select coalesce(previous_seq + 1, 1) as range_from, seq - 1 as range_to
from received
where (previous_seq is null and seq > 1) or seq - previous_seq > 1
```

Dlaczego to wytrzymuje trzy przypadki z sekcji 3.3:

* **(a) duplikaty** — powtórzony `seq` nie tworzy drugiego wiersza (ograniczenie UNIQUE), więc nie podbija
  licznika ani nie „zapycha" luki; zapchać ją może wyłącznie prawdziwe, pierwsze przyjęcie tego numeru.
* **(b) paczki nie po kolei** — odpowiedź liczona jest ze **zbioru** posiadanych numerów, a nie z „ostatniego
  seq". Gdy mam tylko 50–59, brakuje 1–49; po dojściu spóźnionej paczki luka znika sama.
* **(c) trwały brak w środku** — 42 zgłaszane jest jako `{from:42,to:42}` przy każdym zapytaniu, bez
  wygasania. Nie zgaduję, czy jeszcze dojdzie: to decyzja człowieka, API ma tylko mówić prawdę o stanie.

Zakresy zamiast listy pojedynczych numerów, bo przy automacie milczącym przez tydzień lista braków
potrafiłaby mieć dziesiątki tysięcy pozycji. Do tego suma kontrolna `sum(amount_minor)` liczona
w tym samym przebiegu co licznik.

`day` zawęża licznik i sumę do jednej doby, ale **nie** zbiór braków — sekwencja automatu jest ciągła
w czasie, nie dobowa, więc luka „na styku dni" musi być widoczna niezależnie od filtra.

### 4. Próby, backoff i DLQ

Przetworzenie komunikatu i oznaczenie go jako `done` dzieje się w **jednej transakcji**. Nie ma dual-write:
albo powstała projekcja i komunikat jest odhaczony, albo nie ma ani jednego, ani drugiego.

Gdy handler rzuci wyjątkiem, transakcja się wywraca (żadnych połówek projekcji), a księgowanie porażki
idzie w **osobnej** transakcji (`REQUIRES_NEW`): `attempts + 1` i `next_attempt_at = now() + backoff · 2^attempts`.
Po przekroczeniu `inbox.max-attempts` komunikat dostaje status `dead` i treść błędu w `last_error`.
„Zatruty" to u mnie komunikat, którego kolejne próby nie zmieniają — najczęściej niesparsowalny albo
niosący typ zdarzenia, którego nie znamy. Nie blokuje niczego: inne agregaty jadą dalej, bo claim po prostu
bierze następny wiersz. Retry z DLQ przywraca `pending`, zeruje `attempts` i ustawia `next_attempt_at = now()`.

Parametry (`inbox.poll-interval`, `batch-size`, `max-attempts`, `backoff`) siedzą w `application.yaml`;
w profilu testowym są milisekundowe, żeby testy były szybkie i deterministyczne.

### 5. JPA czy JdbcTemplate

Jedno i drugie, świadomie. JPA (`spring-boot-starter-data-jpa`) wnosi zarządzanie transakcjami i obsługuje
prosty odczyt klienta API po tokenie. Cała reszta — wstawki `ON CONFLICT`, claim z `SKIP LOCKED`,
rekoncyliacja z funkcją okna — to `NamedParameterJdbcTemplate`, bo to zapytania, których sens tkwi
w konkretnej składni PostgreSQL. Przepuszczenie ich przez JPA oznaczałoby albo natywne zapytania
(czyli ten sam SQL, tylko schowany za adnotacją), albo — gorzej — próbę odtworzenia idempotencji
w kodzie: `find`, sprawdź, `save`. To dokładnie ten wzorzec, który sypie się przy zrównolegleniu.

Konsekwencja: piszę SQL ręcznie i sam mapuję wiersze, bez cache'a pierwszego poziomu i dirty checkingu.
Przy tej wielkości modelu to zysk, nie strata — wiadomo, jakie zapytanie idzie do bazy.

Encje JPA, repozytoria i mappery są package-private; domena rozmawia z infrastrukturą przez porty
(`InboxRepository`, `OrderRepository`, `VendingSaleRepository`, `ApiClientRepository`).

### 6. Reguła przejść statusu zamówienia

Projekcja to **fold po całej historii zdarzeń**, posortowanej po `occurred_at` (remis rozstrzyga `event_id`),
z deduplikacją po `event_id`. Dzięki temu wynik nie zależy od kolejności dostarczenia — zdarzenia można
podać w dowolnej permutacji i projekcja wyjdzie identyczna (test `OrderEventOrderingTest`).

Znacznik czasu zdarzenia zapisuję **zawsze**, nawet gdy nie zmienia ono statusu. Dlatego „paid" dostarczone
przed „placed" nie ginie, a spóźniony „cancelled" zostaje odnotowany w `cancelledAt`, nie cofając statusu.

Dozwolone przejścia:

```
(brak) → PLACED | PAID | CANCELLED
PLACED → PAID | CANCELLED
PAID   → REFUNDED
CANCELLED → PAID
```

Dwie decyzje wymagają uzasadnienia:

* **`CANCELLED → PAID` jest dozwolone.** Zadanie mówi, że o stanie rozstrzyga najnowsze `occurred_at`.
  Jeśli z zegara nadawcy wychodzi, że anulowanie było o 10:02, a płatność o 10:05, to płatność jest
  faktem późniejszym i to ona ma decydować. Blokowanie jej dawałoby wynik zależny od tego, którą
  parę zdarzeń akurat mamy, a nie od czasów.
* **`PAID → CANCELLED` jest ignorowane.** Anulowanie opłaconego zamówienia to w tym modelu zwrot
  (`REFUNDED`), nie cofnięcie. Zdarzenie trafia do `order_event_log` z efektem `ignored` i zostaje
  do rozpatrzenia przez człowieka — wolę widoczny ślad niż cichą zmianę stanu.

`order_event_log` jest append-only i trzyma efekt (`applied`/`ignored`) wyliczony w momencie przyjęcia
danego zdarzenia. To świadome uproszczenie: gdy później dojdzie zdarzenie starsze, efekty wcześniej
zapisanych wierszy nie są przeliczane wstecz (projekcja — owszem, bo powstaje z całej historii).

## Odpowiedzi na pytania z sekcji 7

### 7.1. Worker zbudował projekcję i zginął przed oznaczeniem komunikatu

Nie zginie mi nic i nie zdubluje się nic, bo te dwie rzeczy są jedną transakcją. Claim (`SELECT ... FOR UPDATE
SKIP LOCKED`), praca handlera i `UPDATE ... SET status = 'done'` dzieją się w tej samej transakcji workera.
Śmierć procesu w dowolnym momencie oznacza rollback: projekcja się nie zapisała, komunikat nigdy nie
opuścił stanu `pending`, blokady (wierszowa i advisory) padły razem z sesją. Po restarcie komunikat jest
po prostu znów widoczny dla claimu. Dlatego w schemacie nie ma stanu „processing" — parkowanie wiersza
w takim stanie wymagałoby dodatkowo reapera zbierającego sieroty po zabitych workerach.

To jednak zabezpiecza tylko przed połową problemu. Druga połowa: komunikat mógł zostać przetworzony
w poprzednim podejściu na tyle, że skutek już jest — na przykład po `markForRetry` z powodu błędu, który
wystąpił po zapisie części danych. Dlatego **handler też jest idempotentny**, niezależnie od transakcji:

* zdarzenie magento wchodzi do `order_event_log` przez `ON CONFLICT (event_id) DO NOTHING`, a projekcja
  jest czystą funkcją całej historii zamówienia i zapisuje się przez upsert. Przetworzenie tego samego
  komunikatu drugi, piąty i setny raz daje bitowo ten sam wiersz w `orders_projection`;
* linia vending wchodzi do `vending_sales` przez `ON CONFLICT (device_id, seq) DO NOTHING`.

Ani jeden, ani drugi handler nie robi niczego przyrostowego (`count = count + 1`, `append`), bo dopiero
takie operacje robią z powtórki problem. Ta własność jest sprawdzana testem: `PoisonMessageTest`
przetwarza komunikat ponownie po retry z DLQ i weryfikuje stan końcowy.

### 7.2. Jeden agregat, zdarzenia z wielu źródeł o różnej gwarancji kolejności

Fundament już jest: identyfikatorem agregatu jest `orderId`, a nie para (źródło, id), i cała projekcja
powstaje z jednego, wspólnego logu zdarzeń przez fold po `occurred_at`. Zwrot zaksięgowany w innym
kanale to po prostu kolejny wiersz w `order_event_log` tego samego zamówienia — mechanika składania
stanu nie zmienia się ani trochę, bo nigdzie nie zakładam, że zdarzenia przychodzą po kolei ani z jednego miejsca.

Co trzeba dołożyć, żeby to było uczciwe przy wielu źródłach:

* **`source_code` w logu zdarzeń.** Dziś log wie „co i kiedy", nie wie „od kogo". Przy jednym źródle to
  nieistotne, przy kilku jest niezbędne do audytu i do rozstrzygania konfliktów.
* **Zaufanie do zegara per źródło.** `occurred_at` pochodzi od nadawcy i jest tak dobre jak jego zegar.
  Przy wielu źródłach różnice rzędu minut przestają być teoretyczne. Wprowadziłbym regułę: jeśli
  `occurred_at` wypada w przyszłości względem `received_at` o więcej niż próg, przycinam do `received_at`
  i zaznaczam to w logu; jeśli źródło jest znane z niedokładnego zegara — priorytet typu zdarzenia
  wygrywa przy remisach czasowych.
* **Reguła rozstrzygania konfliktów zamiast „ostatni wygrywa".** Dwa źródła mogą twierdzić coś sprzecznego
  o tym samym zamówieniu (kanał A: opłacone, kanał B: anulowane). Dziś rozstrzyga to maszyna stanów,
  która jest wspólna dla wszystkich źródeł — i tak powinno zostać: reguła należy do agregatu, nie do kanału.
  Doszłaby natomiast lista typów, dla których konkretne źródło jest autorytatywne (np. zwroty tylko z systemu
  finansowego), a zdarzenia spoza tej listy lądują jako `ignored` z powodem.
* **Kompletność, nie tylko spójność.** Magento nie numeruje zdarzeń, więc dla niego nie mam odpowiednika
  sekwencji z automatów. Przy wielu źródłach dołożyłbym per źródło licznik/kursor i okresową rekoncyliację
  „ile wysłałeś, ile mam" — tak jak dla vendingu, tylko po stronie nadawcy.

### 7.3. Magento zmienia format zdarzenia

Trzy warstwy, z których dwie już działają.

**Surowy payload jest nienaruszalny.** Inbox trzyma to, co przyszło, jako `jsonb`, i nigdy tego nie nadpisuje.
Cokolwiek pójdzie nie tak z interpretacją, oryginał zostaje i można go przetworzyć ponownie po naprawie
mapowania — dokładnie to robi retry z DLQ. To jest najważniejsza własność przy niekontrolowanych nadawcach:
błąd interpretacji nie może być stratą danych.

**Czytanie jest tolerancyjne.** Handler deserializuje payload do wąskiego rekordu z `@JsonIgnoreProperties
(ignoreUnknown = true)` i sięga tylko po pola, których naprawdę potrzebuje. Dodanie pola przez nadawcę
jest więc zmianą niełamiącą — nowe pole po prostu przepływa do inboxu i czeka, aż zaczniemy go używać.

**Granica jest przy tożsamości i przy stanie.** Odrzucam (do DLQ, nie po cichu), gdy:

* brakuje pola tożsamości — `eventId`, `orderId`, `occurredAt`. Bez `eventId` nie da się zagwarantować
  exactly-once, bez `occurredAt` nie da się ustalić kolejności. Zgadywanie oznaczałoby duplikaty albo zły stan;
* typ zdarzenia jest nieznany. Nie mam pojęcia, czy `order.partially_refunded` zmienia stan i w którą stronę.
  Ciche zignorowanie takiego zdarzenia to najgorsza opcja — obraz zamówienia byłby po cichu nieprawdziwy.
  Komunikat parkuje się w DLQ z czytelnym powodem i czeka na decyzję, a po dodaniu obsługi wraca przez retry.

Wersjonowanie kontraktu wejściowego robiłbym przez pole w kopercie (`schemaVersion` albo wersjonowana
nazwa typu, np. `order.paid.v2`) plus mapowanie wersja → parser, żeby stary i nowy format mogły chodzić
równolegle w okresie przejściowym. Ścieżka `/api/v1` wersjonuje **moje** API dla nadawcy; wersja formatu
zdarzenia to co innego i musi mieszkać w treści, bo nadawca zmienia ją wtedy, kiedy chce. Do tego testy
kontraktowe na przykładowych payloadach każdej wersji — plik z próbkami jest tańszy niż uzgadnianie
z dostawcą, co dokładnie zmienił.

### 7.4. Inbox rośnie do dziesiątek milionów wierszy miesięcznie

**Pierwszy pęknie claim** — i to nie dlatego, że wierszy `pending` jest dużo, tylko dlatego, że jest ich mało
wśród ogromu `done`. Dlatego indeks pod pobieranie jest **częściowy**:
`(next_attempt_at, received_at) WHERE status = 'pending'`. Indeksuje wyłącznie kolejkę roboczą, więc jego
rozmiar zależy od zaległości, a nie od historii; wiersz oznaczony jako `done` z niego wypada. Bez tego
planer prędzej czy później zacząłby skanować tabelę, a `SKIP LOCKED` przy zrównolegleniu tylko pogłębia
problem, bo każdy worker skanuje na nowo.

**Drugi problem to martwe krotki i bloat.** Każdy wiersz inboxu jest aktualizowany co najmniej raz
(`pending → done`), więc przy 30 mln komunikatów miesięcznie autovacuum na jednej wielkiej tabeli robi się
wąskim gardłem. Odpowiedź to **partycjonowanie po `received_at`** (miesięcznie albo tygodniowo) i retencja
przez `DETACH PARTITION` zamiast `DELETE` — usunięcie miesiąca staje się operacją metadanych.

Tu jest haczyk, który trzeba rozstrzygnąć świadomie: w Postgresie unikalność na tabeli partycjonowanej
musi zawierać klucz partycjonowania, więc `UNIQUE(source_code, external_id)` przestaje działać globalnie —
a to jest cała moja gwarancja exactly-once. Widzę dwa wyjścia i wybrałbym pierwsze:

1. **Partycjonowanie po `hash(external_id)`** — unikalność zostaje globalna i naturalnie się rozkłada,
   ale retencja wraca do `DELETE` po `received_at` (z indeksem po dacie w każdej partycji).
2. **Partycjonowanie po dacie + osobna, wąska tabela deduplikacyjna** `(source_code, external_id, received_at)`
   z unikalnością, trzymana dłużej niż sam payload. Retencja jest tania, ale mam dwa zapisy zamiast jednego
   i tabelę, która i tak rośnie — tyle że kilkanaście razy wolniej niż inbox z payloadami.

Trzecia rzecz to **rekoncyliacja**, i akurat ona skaluje się dobrze: `lag()` po `vending_sales` czyta zakres
indeksu `UNIQUE(device_id, seq)` dla jednego automatu, a nie całą tabelę — plan to index-only scan po
`(device_id, seq)`. Pierwsza wersja tego zapytania używała `generate_series(1, max(seq))` i to by się właśnie
wywróciło: przy automacie z milionem sprzedaży generowała milion wierszy na każde zapytanie, niezależnie
od tego, że luk było zero. Wymiana na funkcję okna była tego dnia świadomą poprawką (osobny commit).
Przy naprawdę dużych wolumenach doszłaby jeszcze tabela „domknięć": skoro numery poniżej pewnego progu
są kompletne i rozliczone, nie ma powodu ich co dzień przeglądać.

## Zakres dodatkowy (sekcja 6) — jak bym to zrobił

* **6.1 HMAC + ochrona przed replayem** — nadawca liczy `HMAC-SHA256(sekret, timestamp + body)` i wysyła
  w nagłówku razem ze znacznikiem czasu. Filtr (ten sam, który dziś obsługuje token) czyta surowe ciało
  przed deserializacją, porównuje podpis w czasie stałym, odrzuca żądania spoza okna tolerancji (np. ±5 min)
  i te, których podpis już widział — okno plus istniejące `UNIQUE` na inboxie wystarczą za bufor replayowy.
  Fałszywy podpis → 401.
* **6.2 Masowy replay z DLQ** — `POST /inbox/dead-letter/retry` z filtrem (źródło, zakres dat, kod błędu),
  w środku jeden `UPDATE ... WHERE status = 'dead' AND ...` ustawiający `pending`, i raport ile wróciło.
  Mechanizm już istnieje, brakuje tylko wariantu zbiorczego i zliczenia, ile z nich padło ponownie.
* **6.3 Rekoncyliacja dobowa z rozjazdem kwotowym** — endpoint przyjmuje deklarację nadawcy (liczba sztuk
  i suma) i porównuje z `count`/`sum(amount_minor)` po stronie CORE; różnica raportowana jako osobny status.
  Suma kontrolna kwot już jest liczona, brakuje strony „co twierdzi nadawca".
* **6.4 Limit tempa i rozmiaru paczki** — licznik per token w okna czasowe (dla jednej instancji wystarczy
  bucket w pamięci, dla kilku — tabela albo Redis), 429 z `Retry-After`; rozmiar paczki ograniczony
  `spring.servlet.multipart`/`server.max-http-request-header-size` i jawnym limitem liczby linii → 413.
* **6.5 Retencja inboxu** — jak w odpowiedzi 7.4: partycjonowanie i `DETACH` starych partycji, z zachowaniem
  danych deduplikacyjnych dłużej niż payloadów, żeby retencja nie skasowała gwarancji idempotencji.

