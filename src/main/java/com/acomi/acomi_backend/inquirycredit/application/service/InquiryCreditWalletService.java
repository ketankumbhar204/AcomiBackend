package com.acomi.acomi_backend.inquirycredit.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryCreditLedgerEntryType;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditLedgerEntryEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditWalletEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryCreditLedgerRepository;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryCreditWalletRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryCreditWalletService {

    private final InquiryCreditWalletRepository walletRepository;
    private final InquiryCreditLedgerRepository ledgerRepository;

    @Transactional
    public InquiryCreditWalletEntity getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            InquiryCreditWalletEntity wallet = InquiryCreditWalletEntity.builder()
                    .userId(userId)
                    .availableCredits(0)
                    .lifetimeGranted(0)
                    .lifetimeUsed(0)
                    .build();
            return walletRepository.save(wallet);
        });
    }

    @Transactional(readOnly = true)
    public int getBalance(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(InquiryCreditWalletEntity::getAvailableCredits)
                .orElse(0);
    }

    /**
     * Grant credits after a purchase is approved. Idempotent via PURCHASE:{purchaseRequestId}.
     */
    @Transactional
    public void grantPurchase(UUID userId, int credits, UUID purchaseRequestId, UUID adminId) {
        String idempotencyKey = "PURCHASE:" + purchaseRequestId;
        if (ledgerRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.info("wallet_grant_skipped idempotencyKey={} userId={}", idempotencyKey, userId);
            return;
        }

        InquiryCreditWalletEntity wallet = getOrCreateWalletWithLock(userId);
        int newBalance = wallet.getAvailableCredits() + credits;
        wallet.setAvailableCredits(newBalance);
        wallet.setLifetimeGranted(wallet.getLifetimeGranted() + credits);
        walletRepository.save(wallet);

        ledgerRepository.save(InquiryCreditLedgerEntryEntity.builder()
                .walletId(wallet.getId())
                .userId(userId)
                .entryType(InquiryCreditLedgerEntryType.PURCHASE)
                .credits(credits)
                .balanceAfter(newBalance)
                .referenceType("PURCHASE_REQUEST")
                .referenceId(purchaseRequestId)
                .description("Credits granted for approved purchase request")
                .idempotencyKey(idempotencyKey)
                .createdByUserId(adminId)
                .build());

        log.info(
                "wallet_credits_granted userId={} credits={} newBalance={} purchaseRequestId={}",
                userId, credits, newBalance, purchaseRequestId);
    }

    /**
     * Debit 1 credit for a new enquiry. Idempotent via USAGE:{enquiryId}.
     * Throws INSUFFICIENT_INQUIRY_CREDITS (402) if balance is zero.
     */
    @Transactional
    public void consumeUsage(UUID userId, UUID enquiryId) {
        String idempotencyKey = "USAGE:" + enquiryId;
        if (ledgerRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.info("wallet_usage_skipped idempotencyKey={} userId={}", idempotencyKey, userId);
            return;
        }

        InquiryCreditWalletEntity wallet = getOrCreateWalletWithLock(userId);
        if (wallet.getAvailableCredits() <= 0) {
            throw new BusinessException(
                    "INSUFFICIENT_INQUIRY_CREDITS",
                    "You do not have enough inquiry credits.",
                    HttpStatus.PAYMENT_REQUIRED);
        }

        int newBalance = wallet.getAvailableCredits() - 1;
        wallet.setAvailableCredits(newBalance);
        wallet.setLifetimeUsed(wallet.getLifetimeUsed() + 1);
        walletRepository.save(wallet);

        ledgerRepository.save(InquiryCreditLedgerEntryEntity.builder()
                .walletId(wallet.getId())
                .userId(userId)
                .entryType(InquiryCreditLedgerEntryType.USAGE)
                .credits(-1)
                .balanceAfter(newBalance)
                .referenceType("SPACE_ENQUIRY")
                .referenceId(enquiryId)
                .description("1 credit consumed for space enquiry")
                .idempotencyKey(idempotencyKey)
                .build());

        log.info(
                "wallet_credit_consumed userId={} enquiryId={} newBalance={}",
                userId, enquiryId, newBalance);
    }

    private InquiryCreditWalletEntity getOrCreateWalletWithLock(UUID userId) {
        return walletRepository.findByUserIdWithLock(userId).orElseGet(() -> {
            InquiryCreditWalletEntity wallet = InquiryCreditWalletEntity.builder()
                    .userId(userId)
                    .availableCredits(0)
                    .lifetimeGranted(0)
                    .lifetimeUsed(0)
                    .build();
            return walletRepository.save(wallet);
        });
    }
}
