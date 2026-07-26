package com.church.operation.service;

import com.church.operation.dto.ChurchSettingsAdminResponse;
import com.church.operation.dto.ChurchSettingsSaveRequest;
import com.church.operation.entity.ChurchSettings;
import com.church.operation.entity.Member;
import com.church.operation.repo.ChurchSettingsRepository;
import com.church.operation.util.Role;
import com.church.operation.util.SystemAuditOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;

@Service
public class ChurchSettingsService {
    private final ChurchSettingsRepository repository;
    private final ChurchInformationResolver resolver;
    private final ChurchBrandingService branding;
    private final SystemAuditService audit;
    private final RuntimeOperationalSettings operationalSettings;
    private final Clock clock;

    @Autowired
    public ChurchSettingsService(
        ChurchSettingsRepository repository,
        ChurchInformationResolver resolver,
        ChurchBrandingService branding,
        SystemAuditService audit,
        RuntimeOperationalSettings operationalSettings
    ) {
        this(repository, resolver, branding, audit, operationalSettings, Clock.systemUTC());
    }

    ChurchSettingsService(
        ChurchSettingsRepository repository,
        ChurchInformationResolver resolver,
        ChurchBrandingService branding,
        SystemAuditService audit,
        RuntimeOperationalSettings operationalSettings,
        Clock clock
    ) {
        this.repository = repository;
        this.resolver = resolver;
        this.branding = branding;
        this.audit = audit;
        this.operationalSettings = operationalSettings;
        this.clock = clock;
    }

    public ChurchSettingsAdminResponse get(Member actor) {
        requireAdmin(actor);
        boolean saved = resolver.savedSettings().isPresent();
        return response(saved ? "DATABASE" : "SERVER_DEFAULTS");
    }

    public ChurchSettingsAdminResponse save(
        Member actor,
        ChurchSettingsSaveRequest request,
        MultipartFile logo,
        MultipartFile banner
    ) {
        boolean logoChanged = supplied(logo);
        boolean bannerChanged = supplied(banner);
        try {
            requireAdmin(actor);
            NormalizedSettings normalized = validate(request);
            ChurchSettings existing = repository.findById(ChurchSettings.SINGLETON_ID)
                .orElseGet(ChurchSettings::new);
            return replace(actor, normalized, existing, logo, banner, logoChanged, bannerChanged);
        } catch (RuntimeException exception) {
            audit.recordFailure(actor, SystemAuditOperation.CHURCH_SETTINGS_UPDATE, metadata(
                "DATABASE", logoChanged, bannerChanged
            ), exception);
            throw exception;
        }
    }

    public ChurchSettingsAdminResponse reset(Member actor) {
        try {
            requireAdmin(actor);
            Optional<ChurchSettings> existing = repository.findById(ChurchSettings.SINGLETON_ID);
            boolean logoChanged = existing.map(ChurchSettings::getLogoGridFsId).filter(this::hasText).isPresent();
            boolean bannerChanged = existing.map(ChurchSettings::getBannerGridFsId).filter(this::hasText).isPresent();

            repository.deleteById(ChurchSettings.SINGLETON_ID);
            existing.ifPresent(settings -> {
                branding.deleteQuietly(settings.getLogoGridFsId());
                branding.deleteQuietly(settings.getBannerGridFsId());
            });
            ChurchSettingsAdminResponse response = response("SERVER_DEFAULTS");
            audit.recordSuccess(actor, SystemAuditOperation.CHURCH_SETTINGS_RESET, metadata(
                "SERVER_DEFAULTS", logoChanged, bannerChanged, existing.orElse(null), null
            ));
            return response;
        } catch (RuntimeException exception) {
            audit.recordFailure(actor, SystemAuditOperation.CHURCH_SETTINGS_RESET, metadata(
                "SERVER_DEFAULTS", false, false
            ), exception);
            throw exception;
        }
    }

    private ChurchSettingsAdminResponse replace(
        Member actor,
        NormalizedSettings normalized,
        ChurchSettings existing,
        MultipartFile logo,
        MultipartFile banner,
        boolean logoChanged,
        boolean bannerChanged
    ) {
        ChurchBrandingService.StoredBranding newLogo = null;
        ChurchBrandingService.StoredBranding newBanner = null;
        try {
            if (logoChanged) {
                newLogo = branding.store(logo, "logo");
            }
            if (bannerChanged) {
                newBanner = branding.store(banner, "banner");
            }

            ChurchSettings updated = updatedSettings(actor, normalized, existing, newLogo, newBanner);
            repository.save(updated);
        } catch (RuntimeException exception) {
            delete(newLogo);
            delete(newBanner);
            throw exception;
        }

        if (logoChanged) {
            branding.deleteQuietly(existing.getLogoGridFsId());
        }
        if (bannerChanged) {
            branding.deleteQuietly(existing.getBannerGridFsId());
        }
        ChurchSettingsAdminResponse response = response("DATABASE");
        audit.recordSuccess(actor, SystemAuditOperation.CHURCH_SETTINGS_UPDATE, metadata(
            "DATABASE", logoChanged, bannerChanged, existing, normalized
        ));
        return response;
    }

    private ChurchSettings updatedSettings(
        Member actor,
        NormalizedSettings values,
        ChurchSettings existing,
        ChurchBrandingService.StoredBranding logo,
        ChurchBrandingService.StoredBranding banner
    ) {
        ChurchSettings updated = new ChurchSettings();
        updated.setName(values.name());
        updated.setAddress(values.address());
        updated.setContactInfo(values.contactInfo());
        updated.setTreasurerName(values.treasurerName());
        updated.setCharityRegistrationNumber(values.charityRegistrationNumber());
        updated.setReceiptIssueLocation(values.receiptIssueLocation());
        updated.setWebsite(values.website());
        updated.setTimeZone(values.timeZone());
        updated.setFiscalYearStartMonth(values.fiscalYearStartMonth());
        updated.setListPageSize(values.listPageSize());
        updated.setDataOperationExpiry(values.dataOperationExpiry());
        updated.setLogoGridFsId(logo == null ? existing.getLogoGridFsId() : logo.id());
        updated.setLogoContentType(logo == null ? existing.getLogoContentType() : logo.contentType());
        updated.setBannerGridFsId(banner == null ? existing.getBannerGridFsId() : banner.id());
        updated.setBannerContentType(banner == null ? existing.getBannerContentType() : banner.contentType());

        Instant now = clock.instant();
        updated.setCreatedAt(existing.getCreatedAt() == null ? now : existing.getCreatedAt());
        updated.setCreatedByMemberId(existing.getCreatedByMemberId() == null
            ? actor.getId() : existing.getCreatedByMemberId());
        updated.setUpdatedAt(now);
        updated.setUpdatedByMemberId(actor.getId());
        return updated;
    }

    private NormalizedSettings validate(ChurchSettingsSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Church settings are required.");
        }
        String name = required(request.name(), "Church name", 200);
        String address = required(request.address(), "Church address", 500);
        String contactInfo = optional(request.contactInfo(), "Contact information", 500);
        String treasurerName = optional(request.treasurerName(), "Treasurer name", 200);
        String charityNumber = optional(request.charityRegistrationNumber(), "Charity registration number", 200);
        String issueLocation = optional(request.receiptIssueLocation(), "Receipt issue location", 200);
        String website = optional(request.website(), "Church website", 500);
        validateWebsite(website);
        String timeZone = required(request.timeZone(), "Church time zone", 100);
        validateTimeZone(timeZone);
        int fiscalYearStartMonth = request.fiscalYearStartMonth();
        if (fiscalYearStartMonth < 1 || fiscalYearStartMonth > 12) {
            throw new IllegalArgumentException("Fiscal year start month must be between 1 and 12.");
        }
        int listPageSize = request.listPageSize();
        if (listPageSize < 5 || listPageSize > 100) {
            throw new IllegalArgumentException("List page size must be between 5 and 100.");
        }
        long expiryMinutes = request.dataOperationExpiryMinutes();
        if (expiryMinutes < 1 || expiryMinutes > 120) {
            throw new IllegalArgumentException("Data operation expiry must be between 1 and 120 minutes.");
        }
        return new NormalizedSettings(
            name, address, contactInfo, treasurerName, charityNumber, issueLocation, website,
            timeZone, fiscalYearStartMonth, listPageSize, Duration.ofMinutes(expiryMinutes)
        );
    }

    private void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Church time zone must be a valid IANA time zone.");
        }
    }

    private void validateWebsite(String website) {
        if (website.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(website);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Church website must use a valid HTTP or HTTPS address.");
        }
    }

    private String required(String value, String label, int maxLength) {
        String normalized = optional(value, label, maxLength);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private String optional(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        }
        return normalized;
    }

    private ChurchSettingsAdminResponse response(String source) {
        EffectiveChurchInformation effective = resolver.resolve();
        RuntimeOperationalSettings.EffectiveSettings operations = operationalSettings.resolve();
        return new ChurchSettingsAdminResponse(
            effective.name(), effective.address(), effective.contactInfo(), effective.treasurerName(),
            effective.charityRegistrationNumber(), effective.receiptIssueLocation(), effective.website(),
            effective.logoUrl(), effective.bannerUrl(), operations.timeZone().getId(),
            operations.fiscalYearStartMonth(), operations.listPageSize(),
            operations.dataOperationExpiry().toMinutes(), source, effective.updatedAt()
        );
    }

    private void requireAdmin(Member actor) {
        if (actor == null || actor.getRoles() == null || !actor.getRoles().contains(Role.ADMIN)) {
            throw new SecurityException("Administrator access is required.");
        }
    }

    private Map<String, ?> metadata(String source, boolean logoChanged, boolean bannerChanged) {
        return metadata(source, logoChanged, bannerChanged, null, null);
    }

    private Map<String, ?> metadata(
        String source,
        boolean logoChanged,
        boolean bannerChanged,
        ChurchSettings existing,
        NormalizedSettings values
    ) {
        return Map.of(
            "configurationSource", source,
            "logoChanged", logoChanged,
            "bannerChanged", bannerChanged,
            "timeZoneChanged", operationalChanged(
                existing == null ? null : existing.getTimeZone(),
                values == null ? null : values.timeZone(), values == null
            ),
            "fiscalYearStartMonthChanged", operationalChanged(
                existing == null ? null : existing.getFiscalYearStartMonth(),
                values == null ? null : values.fiscalYearStartMonth(), values == null
            ),
            "listPageSizeChanged", operationalChanged(
                existing == null ? null : existing.getListPageSize(),
                values == null ? null : values.listPageSize(), values == null
            ),
            "dataOperationExpiryChanged", operationalChanged(
                existing == null ? null : existing.getDataOperationExpiry(),
                values == null ? null : values.dataOperationExpiry(), values == null
            )
        );
    }

    private boolean operationalChanged(Object existing, Object replacement, boolean reset) {
        return reset ? existing != null : !Objects.equals(existing, replacement);
    }

    private boolean supplied(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void delete(ChurchBrandingService.StoredBranding stored) {
        if (stored != null) {
            branding.deleteQuietly(stored.id());
        }
    }

    private record NormalizedSettings(
        String name,
        String address,
        String contactInfo,
        String treasurerName,
        String charityRegistrationNumber,
        String receiptIssueLocation,
        String website,
        String timeZone,
        int fiscalYearStartMonth,
        int listPageSize,
        Duration dataOperationExpiry
    ) {
    }
}
