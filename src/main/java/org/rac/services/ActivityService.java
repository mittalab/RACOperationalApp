package org.rac.services;

import org.rac.model.Activity;
import org.rac.model.SendResultsActivity;

import java.util.ArrayList;
import java.util.List;

public class ActivityService {

    public List<Activity> getActivities() {
        List<Activity> activities = new ArrayList<>();
        activities.add(new SendResultsActivity());
        // Future activities can be added here
        return activities;
    }
}
