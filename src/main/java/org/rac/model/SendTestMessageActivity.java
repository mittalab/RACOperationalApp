package org.rac.model;

public class SendTestMessageActivity implements Activity {

    @Override
    public String getName() { return "Send Test Message"; }

    @Override
    public String getFxmlPath() { return "/org/rac/gui/SendTestMessageView.fxml"; }

    @Override
    public String getIcon() { return "✉"; }
}
