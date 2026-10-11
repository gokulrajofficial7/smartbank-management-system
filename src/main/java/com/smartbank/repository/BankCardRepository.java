package com.smartbank.repository;

import com.smartbank.entity.BankCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankCardRepository extends JpaRepository<BankCard, Long> {

    List<BankCard> findByAccountId(Long accountId);

    Optional<BankCard> findByCardNumber(String cardNumber);
}
