package com.universalplatform.tenancy;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.Period;
import java.util.logging.Logger;

@Service
public class LicenseExpiryChecker {

    private static final Logger logger = Logger.getLogger(LicenseExpiryChecker.class.getName());

    @Autowired
    private ClientRepository clientRepository;

    @Value("${platform.licensing.notification.days-before-expiry:30}")
    private int notificationDaysBeforeExpiry;

    @PostConstruct
    public void init() {
        // Optional initial run
    }

    @Scheduled(fixedRate = 86400000) // run once per day
    public void checkExpiry() {
        LocalDate today = LocalDate.now();
        clientRepository.findAll().forEach(client -> {
            if (client.getLicensingPeriodEnd() != null) {
                Period period = Period.between(today, client.getLicensingPeriodEnd());
                int daysLeft = period.getDays() + period.getMonths() * 30 + period.getYears() * 365;
                if (daysLeft <= notificationDaysBeforeExpiry && daysLeft > 0) {
                    logger.info(() -> "License for client " + client.getName() + " expires in " + daysLeft + " days. Consider renewal.");
                }
                if (daysLeft <= 0) {
                    logger.warning(() -> "License for client " + client.getName() + " has expired.");
                }
            }
        });
    }
}