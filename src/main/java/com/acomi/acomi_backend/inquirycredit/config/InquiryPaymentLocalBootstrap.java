package com.acomi.acomi_backend.inquirycredit.config;

import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditPackageEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryPaymentConfigEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryCreditPackageRepository;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryPaymentConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Local-only: turn on the ₹9 / 30-credit purchase flow so seekers can test UPI + WhatsApp
 * screenshot verification without waiting for a production admin toggle.
 */
@Component
@Profile("local")
public class InquiryPaymentLocalBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InquiryPaymentLocalBootstrap.class);

    private final InquiryPaymentConfigRepository configRepository;
    private final InquiryCreditPackageRepository packageRepository;
    private final boolean enablePurchases;
    private final String whatsappNumber;
    private final String upiId;

    public InquiryPaymentLocalBootstrap(
            InquiryPaymentConfigRepository configRepository,
            InquiryCreditPackageRepository packageRepository,
            @Value("${acomi.inquiry.enable-purchases:false}") boolean enablePurchases,
            @Value("${acomi.inquiry.whatsapp-number:}") String whatsappNumber,
            @Value("${acomi.inquiry.upi-id:}") String upiId) {
        this.configRepository = configRepository;
        this.packageRepository = packageRepository;
        this.enablePurchases = enablePurchases;
        this.whatsappNumber = whatsappNumber;
        this.upiId = upiId;
    }

    @Override
    public void run(ApplicationArguments args) {
        InquiryPaymentConfigEntity config =
                configRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        if (config == null) {
            log.warn("inquiry_payment_local_bootstrap_skipped reason=config-missing");
            return;
        }
        boolean dirty = false;
        if (enablePurchases && !config.isEnabled()) {
            config.setEnabled(true);
            dirty = true;
        }
        if (StringUtils.hasText(whatsappNumber) && !StringUtils.hasText(config.getWhatsappNumber())) {
            config.setWhatsappNumber(whatsappNumber.trim());
            dirty = true;
        }
        if (StringUtils.hasText(upiId) && !StringUtils.hasText(config.getUpiId())) {
            config.setUpiId(upiId.trim());
            dirty = true;
        }
        if (dirty) {
            configRepository.save(config);
            log.info("inquiry_payment_local_bootstrap_updated purchasesEnabled={}", config.isEnabled());
        }
        for (InquiryCreditPackageEntity pkg : packageRepository.findAll()) {
            if (pkg.getClientChannel() == InquiryClientChannel.WEB && !pkg.isEnabled()) {
                pkg.setEnabled(true);
                packageRepository.save(pkg);
                log.info("inquiry_payment_local_bootstrap_package_enabled channel=WEB");
            }
        }
    }
}
