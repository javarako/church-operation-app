package com.church.operation.entity;

import com.church.operation.util.ReferenceDataType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("referenceData")
@CompoundIndex(name = "type_code_unique", def = "{'type': 1, 'code': 1}", unique = true)
public class ReferenceData {
    @Id
    private String id;

    private ReferenceDataType type;
    private String code;
    private String label;
    private int sortOrder;
    private boolean active = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public ReferenceDataType getType() { return type; }
    public void setType(ReferenceDataType type) { this.type = type; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
