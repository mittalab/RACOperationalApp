package org.rac.model;

public class Student {
    private final String name;
    private final double marksObtained;
    private final String additionalDetails;
    private final String phone;
    private final String rollNo;

    public Student(String name, double marksObtained, String additionalDetails, String phone, String rollNo) {
        this.name = name.toUpperCase();
        this.marksObtained = marksObtained;
        this.additionalDetails = additionalDetails;
        this.phone = phone;
        this.rollNo = rollNo;
    }

    public String getName() { return name; }
    public double getMarksObtained() { return marksObtained; }
    public String getAdditionalDetails() { return additionalDetails; }
    public String getPhone() { return phone; }
    public String getRollNo() { return rollNo; }

    @Override
    public String toString() {
        return "Student{name='" + name + "', marksObtained=" + marksObtained
                + ", phone='" + phone + "', rollNo='" + rollNo + "'}";
    }
}
