package org.rac.model;

public class Student {
    private final String name;
    private final double marksObtained;
    private final String additionalDetails;

    public Student(String name, double marksObtained, String additionalDetails) {
        this.name = name.toUpperCase();
        this.marksObtained = marksObtained;
        this.additionalDetails = additionalDetails;
    }
    public String getName() {
        return name;
    }

    public double getMarksObtained() {
        return marksObtained;
    }

    public String getAdditionalDetails() {
        return additionalDetails;
    }


    @Override
    public String toString() {
        return "Student{"
                + "name='" + name + "'\''" +
                ", marksObtained=" + marksObtained +
                ", additionalDetails='" + additionalDetails + "'\''" +
                '}';
    }
}
