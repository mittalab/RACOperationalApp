package org.rac.model;

public class CheckWamidStatusActivity implements Activity {

    @Override
    public String getName() { return "Check WhatsApp Status"; }

    @Override
    public String getFxmlPath() { return "/org/rac/gui/CheckWamidStatusView.fxml"; }

    @Override
    public String getIcon() { return "?"; }
}
