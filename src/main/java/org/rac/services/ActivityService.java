package org.rac.services;

import org.rac.model.Activity;
import org.rac.model.CheckWamidStatusActivity;
import org.rac.model.RetrieveRunStatusActivity;
import org.rac.model.SendResultsActivity;
import org.rac.model.SendTestMessageActivity;
import org.rac.model.SendTopperAbsentActivity;

import java.util.ArrayList;
import java.util.List;

public class ActivityService {

    public List<Activity> getActivities() {
        List<Activity> activities = new ArrayList<>();
        activities.add(new SendResultsActivity());
        activities.add(new SendTopperAbsentActivity());
        activities.add(new CheckWamidStatusActivity());
        activities.add(new RetrieveRunStatusActivity());
        activities.add(new SendTestMessageActivity());
        return activities;
    }
}
