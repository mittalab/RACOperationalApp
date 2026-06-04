package org.rac.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class MessageDelivery {

    private final StringProperty studentName  = new SimpleStringProperty();
    private final StringProperty phone        = new SimpleStringProperty();
    private final StringProperty messageId    = new SimpleStringProperty();
    private final StringProperty status       = new SimpleStringProperty("Sent");
    private final StringProperty lastChecked  = new SimpleStringProperty("—");

    public MessageDelivery(String studentName, String phone, String messageId) {
        this.studentName.set(studentName);
        this.phone.set(phone);
        this.messageId.set(messageId != null ? messageId : "");
    }

    public MessageDelivery(String studentName, String phone, String messageId, String status) {
        this.studentName.set(studentName);
        this.phone.set(phone);
        this.messageId.set(messageId != null ? messageId : "");
        this.status.set(status);
    }

    // --- Properties (required by PropertyValueFactory in TableView) ---
    public StringProperty studentNameProperty() { return studentName; }
    public StringProperty phoneProperty()       { return phone; }
    public StringProperty messageIdProperty()   { return messageId; }
    public StringProperty statusProperty()      { return status; }
    public StringProperty lastCheckedProperty() { return lastChecked; }

    // --- Getters ---
    public String getStudentName() { return studentName.get(); }
    public String getPhone()       { return phone.get(); }
    public String getMessageId()   { return messageId.get(); }
    public String getStatus()      { return status.get(); }
    public String getLastChecked() { return lastChecked.get(); }

    // --- Setters (called from polling thread via Platform.runLater) ---
    public void setStatus(String s)      { status.set(s); }
    public void setLastChecked(String t) { lastChecked.set(t); }

    /** True if this record still needs polling (not yet terminal). */
    public boolean isPending() {
        String s = status.get();
        return s == null || s.equals("Sent") || s.equals("Accepted") || s.equals("Checking…");
    }
}
