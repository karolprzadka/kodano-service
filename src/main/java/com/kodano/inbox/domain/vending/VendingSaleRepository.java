package com.kodano.inbox.domain.vending;

import java.time.LocalDate;
import java.util.List;

public interface VendingSaleRepository {

   void insertIfAbsent(VendingSale sale);

   long countSales(String deviceId, LocalDate day);

   List<Long> findMissingSeqs(String deviceId);
}
