package org.rac.model;

public class Student {
    private final String name;
    private final String phoneNumber;
    private final double marksObtained;
    private final String additionalDetails;

    private final String email;

    public Student(String name, String phoneNumber, double marksObtained, String additionalDetails, String email) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.marksObtained = marksObtained;
        this.additionalDetails = additionalDetails;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public double getMarksObtained() {
        return marksObtained;
    }

    public String getAdditionalDetails() {
        return additionalDetails;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        return "Student{"
                + "name='" + name + "'\''" +
                ", phoneNumber='" + phoneNumber + "'\''" +
                ", marksObtained=" + marksObtained +
                ", additionalDetails='" + additionalDetails + "'\''" +
                ", email='" + email + "'\''" +
                '}';
    }
}
