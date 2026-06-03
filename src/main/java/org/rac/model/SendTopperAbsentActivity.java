package org.rac.model;

public class SendTopperAbsentActivity implements Activity {

    @Override
    public String getName() {
        return "Send Topper & Absent List";
    }

    @Override
    public String getFxmlPath() {
        return "/org/rac/gui/SendAdminNotificationsView.fxml";
    }

    @Override
    public String getIcon() { return "★"; }

    @Override
    public String toString() {
        return getName();
    }
}
