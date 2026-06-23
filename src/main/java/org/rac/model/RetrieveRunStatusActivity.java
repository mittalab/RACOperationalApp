package org.rac.model;

public class RetrieveRunStatusActivity implements Activity {

    @Override
    public String getName() {
        return "Retrieve Run Status";
    }

    @Override
    public String getFxmlPath() {
        return "/org/rac/gui/RetrieveRunStatusView.fxml";
    }

    @Override
    public String getIcon() {
        return "↺";
    }

    @Override
    public String toString() {
        return getName();
    }
}
