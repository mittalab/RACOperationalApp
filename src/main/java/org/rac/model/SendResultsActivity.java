package org.rac.model;

public class SendResultsActivity implements Activity {

    @Override
    public String getName() {
        return "Send Results to Parents";
    }

    @Override
    public String getFxmlPath() {
        return "/org/rac/gui/SendResultsView.fxml";
    }

    @Override
    public String toString() {
        return getName();
    }
}
