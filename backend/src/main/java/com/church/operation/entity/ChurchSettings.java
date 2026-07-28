package com.church.operation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.Duration;

@Document("church_settings")
public class ChurchSettings {
    public static final String SINGLETON_ID = "church-settings";

    @Id
    private String id = SINGLETON_ID;
    private String name;
    private String address;
    private String contactInfo;
    private String treasurerName;
    private String charityRegistrationNumber;
    private String receiptIssueLocation;
    private String website;
    private String logoGridFsId;
    private String logoContentType;
    private String bannerGridFsId;
    private String bannerContentType;
    private String timeZone;
    private Integer fiscalYearStartMonth;
    private Integer listPageSize;
    private Duration dataOperationExpiry;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdByMemberId;
    private String updatedByMemberId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
    public String getTreasurerName() { return treasurerName; }
    public void setTreasurerName(String treasurerName) { this.treasurerName = treasurerName; }
    public String getCharityRegistrationNumber() { return charityRegistrationNumber; }
    public void setCharityRegistrationNumber(String charityRegistrationNumber) { this.charityRegistrationNumber = charityRegistrationNumber; }
    public String getReceiptIssueLocation() { return receiptIssueLocation; }
    public void setReceiptIssueLocation(String receiptIssueLocation) { this.receiptIssueLocation = receiptIssueLocation; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getLogoGridFsId() { return logoGridFsId; }
    public void setLogoGridFsId(String logoGridFsId) { this.logoGridFsId = logoGridFsId; }
    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String logoContentType) { this.logoContentType = logoContentType; }
    public String getBannerGridFsId() { return bannerGridFsId; }
    public void setBannerGridFsId(String bannerGridFsId) { this.bannerGridFsId = bannerGridFsId; }
    public String getBannerContentType() { return bannerContentType; }
    public void setBannerContentType(String bannerContentType) { this.bannerContentType = bannerContentType; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public Integer getFiscalYearStartMonth() { return fiscalYearStartMonth; }
    public void setFiscalYearStartMonth(Integer fiscalYearStartMonth) { this.fiscalYearStartMonth = fiscalYearStartMonth; }
    public Integer getListPageSize() { return listPageSize; }
    public void setListPageSize(Integer listPageSize) { this.listPageSize = listPageSize; }
    public Duration getDataOperationExpiry() { return dataOperationExpiry; }
    public void setDataOperationExpiry(Duration dataOperationExpiry) { this.dataOperationExpiry = dataOperationExpiry; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedByMemberId() { return createdByMemberId; }
    public void setCreatedByMemberId(String createdByMemberId) { this.createdByMemberId = createdByMemberId; }
    public String getUpdatedByMemberId() { return updatedByMemberId; }
    public void setUpdatedByMemberId(String updatedByMemberId) { this.updatedByMemberId = updatedByMemberId; }
}
