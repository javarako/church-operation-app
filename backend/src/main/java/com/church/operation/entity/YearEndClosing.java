package com.church.operation.entity;

import com.church.operation.util.YearEndClosingStatus;
import com.church.operation.util.YearEndReportType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("year_end_closings")
@CompoundIndexes({
    @CompoundIndex(
        name = "year_end_type_version",
        def = "{'fiscalYear': 1, 'reportType': 1, 'version': 1}",
        unique = true
    )
})
public class YearEndClosing {
    @Id
    private String id;
    @Indexed
    private int fiscalYear;
    @Indexed
    private YearEndReportType reportType;
    private int version;
    @Indexed
    private YearEndClosingStatus status;
    @Indexed
    private boolean active;
    @Indexed(unique = true, sparse = true)
    private String activeKey;
    private String gridFsFileId;
    private String filename;
    private String contentType;
    private long fileSize;
    private String checksum;
    private String closedByMemberId;
    private String closedByEmail;
    private Instant closedAt;
    private String reopenedByMemberId;
    private String reopenedByEmail;
    private Instant reopenedAt;

    public static String activeKey(int fiscalYear, YearEndReportType reportType) {
        return fiscalYear + ":" + reportType.name();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(int fiscalYear) { this.fiscalYear = fiscalYear; }
    public YearEndReportType getReportType() { return reportType; }
    public void setReportType(YearEndReportType reportType) { this.reportType = reportType; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public YearEndClosingStatus getStatus() { return status; }
    public void setStatus(YearEndClosingStatus status) { this.status = status; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getActiveKey() { return activeKey; }
    public void setActiveKey(String activeKey) { this.activeKey = activeKey; }
    public String getGridFsFileId() { return gridFsFileId; }
    public void setGridFsFileId(String gridFsFileId) { this.gridFsFileId = gridFsFileId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getClosedByMemberId() { return closedByMemberId; }
    public void setClosedByMemberId(String closedByMemberId) { this.closedByMemberId = closedByMemberId; }
    public String getClosedByEmail() { return closedByEmail; }
    public void setClosedByEmail(String closedByEmail) { this.closedByEmail = closedByEmail; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public String getReopenedByMemberId() { return reopenedByMemberId; }
    public void setReopenedByMemberId(String reopenedByMemberId) { this.reopenedByMemberId = reopenedByMemberId; }
    public String getReopenedByEmail() { return reopenedByEmail; }
    public void setReopenedByEmail(String reopenedByEmail) { this.reopenedByEmail = reopenedByEmail; }
    public Instant getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(Instant reopenedAt) { this.reopenedAt = reopenedAt; }
}
