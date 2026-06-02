package org.rac.services;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rac.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ExcelReaderService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelReaderService.class);

    public static class ExcelReadResult {
        public final List<Student> students;
        public final List<String> validationErrors;
        public final boolean success;
        public final List<String> absentStudentNames;
        public String targetSheetName; // New field

        ExcelReadResult(List<Student> students, List<String> validationErrors, List<String> absentStudentNames) {
            this.students = students;
            this.validationErrors = validationErrors;
            this.success = validationErrors.isEmpty();
            this.absentStudentNames = Collections.unmodifiableList(absentStudentNames);
        }
    }

    /**
     * @param useContactSheet true for standard two-sheet files (Result + Contact);
     *                        false for single-sheet files where col 3 holds the phone number.
     */
    public ExcelReadResult readAndValidate(File marksFile, File cloudContactsFile, String className, String batch) throws IOException {
        logger.info("Reading Marks file: {}, Cloud Contacts file: {}, Class: {}, Batch: {}", 
                marksFile.getAbsolutePath(), cloudContactsFile != null ? cloudContactsFile.getAbsolutePath() : "None",
                className, batch);
        
        List<String> errors = new ArrayList<>();
        List<Student> students = new ArrayList<>();
        List<String> absentNames = new ArrayList<>();

        try (FileInputStream fisMarks = new FileInputStream(marksFile);
             Workbook workbookMarks = new XSSFWorkbook(fisMarks)) {

            Sheet resultSheet = workbookMarks.getSheet("Result");
            if (resultSheet == null) {
                logger.warn("Sheet 'Result' not found in marks file, falling back to sheet index 0");
                resultSheet = workbookMarks.getSheetAt(0);
            }

            if (cloudContactsFile != null) {
                try (FileInputStream fisCloud = new FileInputStream(cloudContactsFile);
                     Workbook workbookCloud = new XSSFWorkbook(fisCloud)) {
                    readWithExternalContacts(resultSheet, workbookCloud, students, errors, absentNames, className, batch);
                }
            } else {
                // Fallback to legacy behavior: check if Contact sheet exists in same file
                Sheet contactSheet = workbookMarks.getSheet("Contact");
                if (contactSheet != null) {
                    readTwoSheetFormat(workbookMarks, students, errors, absentNames);
                } else {
                    readSingleSheetFormat(workbookMarks, students, errors);
                }
            }

            logger.info("Read {} students, {} validation errors, {} absent", students.size(), errors.size(), absentNames.size());
        }

        ExcelReadResult finalResult = new ExcelReadResult(students, errors, absentNames);
        if (cloudContactsFile != null) {
            finalResult.targetSheetName = findTargetSheetName(className, batch);
        }
        return finalResult;
    }

    private void readWithExternalContacts(Sheet resultSheet, Workbook contactWorkbook, 
                                        List<Student> students, List<String> errors, List<String> absentNames,
                                        String className, String batch) {
        // Build contact lookup from matched sheets
        Map<String, String> nameToPhone = new LinkedHashMap<>();
        Map<String, String> rollNoToPhone = new LinkedHashMap<>();
        Map<String, String> upperToOriginal = new LinkedHashMap<>();
        Set<String> duplicateNames = new HashSet<>();

        String targetSheetName = findTargetSheetName(className, batch);
        logger.info("Targeting sheet: '{}' for class '{}' and batch '{}'", targetSheetName, className, batch);

        boolean sheetFound = false;
        for (int i = 0; i < contactWorkbook.getNumberOfSheets(); i++) {
            Sheet sheet = contactWorkbook.getSheetAt(i);
            String currentSheetName = sheet.getSheetName().trim();
            
            // Hard mapping: only exact match allowed
            if (targetSheetName == null || !currentSheetName.equalsIgnoreCase(targetSheetName)) {
                continue;
            }
            
            sheetFound = true;
            logger.debug("Processing matched contact sheet: {}", sheet.getSheetName());
            
            Iterator<Row> rows = sheet.iterator();
            if (rows.hasNext()) rows.next(); // skip header line

            while (rows.hasNext()) {
                Row row = rows.next();
                String rawName = getCellStringValue(row.getCell(2)).trim(); // Column C: Student Name
                String name = rawName.toUpperCase();
                String phone = getCellStringValue(row.getCell(3)).trim();  // Column D: Phone Number
                String rollNo = getCellStringValue(row.getCell(4)).trim(); // Column E: Roll Number

                if (name.isEmpty()) continue;

                if (nameToPhone.containsKey(name)) {
                    if (!nameToPhone.get(name).equals(phone)) {
                        duplicateNames.add(name);
                    }
                } else {
                    nameToPhone.put(name, phone);
                    upperToOriginal.put(name, rawName);
                }

                if (!rollNo.isEmpty()) rollNoToPhone.put(rollNo, phone);
            }
        }

        if (!sheetFound) {
            errors.add("Could not find a matching contact sheet in Google Sheets for Class: " + className + ", Batch: " + batch);
            return;
        }

        logger.info("Aggregated contacts: {} unique names from matched sheets", nameToPhone.size());

        // Now process results and lookup
        Iterator<Row> resultRows = resultSheet.iterator();
        if (resultRows.hasNext()) resultRows.next(); // skip header

        int rowNum = 1;
        while (resultRows.hasNext()) {
            Row row = resultRows.next();
            rowNum++;
            try {
                String name = getCellStringValue(row.getCell(0)).trim();
                if (name.isEmpty()) continue;

                Cell marksCell = row.getCell(1);
                if (marksCell == null) {
                    errors.add(name + ": missing marks (row " + rowNum + ")");
                    continue;
                }
                double marks = marksCell.getNumericCellValue();
                String additionalDetails = getCellStringValue(row.getCell(2));
                String rollNo = getCellStringValue(row.getCell(3)).trim();

                String upperName = name.toUpperCase();
                String phone = resolvePhone(upperName, rollNo, nameToPhone, rollNoToPhone, duplicateNames, errors);

                if (phone != null) {
                    String e = validatePhone(upperName, phone);
                    if (e != null) { errors.add(e); phone = null; }
                }

                students.add(new Student(name, marks, additionalDetails, phone != null ? phone : "", rollNo));
            } catch (Exception e) {
                logger.error("Error reading row {}", rowNum, e);
                errors.add("Row " + rowNum + ": " + e.getMessage());
            }
        }

        // Absent logic: Only check if student exists in ANY of the contact sheets but not in Result
        Set<String> resultUpperNames = new HashSet<>();
        for (Student s : students) resultUpperNames.add(s.getName().toUpperCase());
        for (String upperName : nameToPhone.keySet()) {
            if (!resultUpperNames.contains(upperName)) {
                absentNames.add(upperToOriginal.getOrDefault(upperName, upperName));
            }
        }
    }

    /**
     * @deprecated Use {@link #readAndValidate(File, File, String, String)}
     */
    @Deprecated
    public ExcelReadResult readAndValidate(File file, boolean useContactSheet) throws IOException {
        return readAndValidate(file, null, "", "");
    }

    private void readTwoSheetFormat(Workbook workbook, List<Student> students, List<String> errors, List<String> absentNames) {
        Sheet resultSheet = workbook.getSheet("Result");
        if (resultSheet == null) {
            logger.warn("Sheet 'Result' not found, falling back to sheet index 0");
            resultSheet = workbook.getSheetAt(0);
        }

        Sheet contactSheet = workbook.getSheet("Contact");
        if (contactSheet == null) {
            logger.warn("Sheet 'Contact' not found, falling back to sheet index 1");
            contactSheet = workbook.getSheetAt(1);
        }

        // Build contact lookup: name → phone (unique names only), rollNo → phone
        Map<String, String> nameToPhone = new LinkedHashMap<>();
        Map<String, String> rollNoToPhone = new LinkedHashMap<>();
        Map<String, String> upperToOriginal = new LinkedHashMap<>(); // uppercase key → original-case display name
        Set<String> duplicateNames = new HashSet<>();

        Iterator<Row> contactRows = contactSheet.iterator();
        if (contactRows.hasNext()) contactRows.next(); // skip header

        while (contactRows.hasNext()) {
            Row row = contactRows.next();
            String rawName = getCellStringValue(row.getCell(0)).trim();
            String name = rawName.toUpperCase();
            String phone = getCellStringValue(row.getCell(1)).trim();
            String rollNo = getCellStringValue(row.getCell(2)).trim();

            if (name.isEmpty()) continue;

            if (nameToPhone.containsKey(name)) {
                duplicateNames.add(name);
                nameToPhone.remove(name);
                upperToOriginal.remove(name);
            } else if (!duplicateNames.contains(name)) {
                nameToPhone.put(name, phone);
                upperToOriginal.put(name, rawName);
            }

            if (!rollNo.isEmpty()) rollNoToPhone.put(rollNo, phone);
        }

        logger.debug("Contact sheet: {} unique names, {} duplicates, {} roll no entries",
                nameToPhone.size(), duplicateNames.size(), rollNoToPhone.size());

        Iterator<Row> resultRows = resultSheet.iterator();
        if (resultRows.hasNext()) resultRows.next(); // skip header

        int rowNum = 1;
        while (resultRows.hasNext()) {
            Row row = resultRows.next();
            rowNum++;
            try {
                String name = getCellStringValue(row.getCell(0)).trim();
                if (name.isEmpty()) continue;

                Cell marksCell = row.getCell(1);
                if (marksCell == null) {
                    errors.add(name + ": missing marks in Result tab (row " + rowNum + ")");
                    continue;
                }
                double marks = marksCell.getNumericCellValue();
                String additionalDetails = getCellStringValue(row.getCell(2));
                String rollNo = getCellStringValue(row.getCell(3)).trim();

                String upperName = name.toUpperCase();
                String phone = resolvePhone(upperName, rollNo, nameToPhone, rollNoToPhone, duplicateNames, errors);

                if (phone != null) {
                    String e = validatePhone(upperName, phone);
                    if (e != null) { errors.add(e); phone = null; }
                }

                students.add(new Student(name, marks, additionalDetails, phone != null ? phone : "", rollNo));
                logger.trace("Read student row {}: {}", rowNum, name);
            } catch (Exception e) {
                logger.error("Could not read student data from row {}", rowNum, e);
                errors.add("Row " + rowNum + ": could not be read (" + e.getMessage() + ")");
            }
        }

        // Compute absent: in Contact but not in Result sheet
        Set<String> resultUpperNames = new HashSet<>();
        for (Student s : students) resultUpperNames.add(s.getName().toUpperCase());
        for (String upperName : nameToPhone.keySet()) {
            if (!resultUpperNames.contains(upperName)) {
                absentNames.add(upperToOriginal.getOrDefault(upperName, upperName));
            }
        }
        logger.debug("Absent students: {}", absentNames.size());
    }

    private void readSingleSheetFormat(Workbook workbook, List<Student> students, List<String> errors) {
        Sheet sheet = workbook.getSheetAt(0);

        Iterator<Row> rows = sheet.iterator();
        if (rows.hasNext()) rows.next(); // skip header

        int rowNum = 1;
        while (rows.hasNext()) {
            Row row = rows.next();
            rowNum++;
            try {
                String name = getCellStringValue(row.getCell(0)).trim();
                if (name.isEmpty()) continue;

                Cell marksCell = row.getCell(1);
                if (marksCell == null) {
                    errors.add(name + ": missing marks (row " + rowNum + ")");
                    continue;
                }
                double marks = marksCell.getNumericCellValue();
                String additionalDetails = getCellStringValue(row.getCell(2));
                String phone = getCellStringValue(row.getCell(3)).trim();

                String upperName = name.toUpperCase();
                if (phone.isEmpty()) {
                    errors.add(upperName + ": missing phone number (row " + rowNum + ")");
                    phone = null;
                } else {
                    String e = validatePhone(upperName, phone);
                    if (e != null) { errors.add(e); phone = null; }
                }

                students.add(new Student(name, marks, additionalDetails, phone != null ? phone : "", ""));
                logger.trace("Read student row {}: {}", rowNum, name);
            } catch (Exception e) {
                logger.error("Could not read student data from row {}", rowNum, e);
                errors.add("Row " + rowNum + ": could not be read (" + e.getMessage() + ")");
            }
        }
    }

    private String findTargetSheetName(String className, String batch) {
        // Strict hard mapping: maps Class + UI Batch to EXACT Google Sheet Name
        String c = className.trim().toUpperCase();
        String b = batch.trim().toUpperCase();

        if (c.equals("X")) {
            if (b.contains("MONDAY")) return "RAC Monday(Science) X, 26";
            if (b.contains("6-7"))  return "RAC X Science ,6-7pm (2026)";
            if (b.contains("7-8"))  return "RAC X Science,7-8 (2026)";
        } else if (c.equals("IX")) {
            if (b.contains("MONDAY"))  return "RAC IX Science Monday, 2026";
            if (b.contains("TUESDAY")) return "RAC IX Science, Tuesday 2026";
        }
        return null;
    }

    private String resolvePhone(String upperName, String rollNo,
                                Map<String, String> nameToPhone,
                                Map<String, String> rollNoToPhone,
                                Set<String> duplicateNames,
                                List<String> errors) {
        if (duplicateNames.contains(upperName)) {
            if (!rollNo.isEmpty() && rollNoToPhone.containsKey(rollNo)) {
                return rollNoToPhone.get(rollNo);
            }
            errors.add(upperName + ": duplicate name in Contact tab with no Roll No to distinguish");
            return null;
        }
        if (nameToPhone.containsKey(upperName)) {
            return nameToPhone.get(upperName);
        }
        errors.add(upperName + ": not found in Contact tab");
        return null;
    }

    private String validatePhone(String name, String phone) {
        String[] parts = phone.split(",");
        List<String> invalid = new ArrayList<>();
        for (String part : parts) {
            String digits = part.trim().replaceAll("\\D", "");
            if (digits.length() != 10 && !(digits.length() == 12 && digits.startsWith("91"))) {
                invalid.add(part.trim());
            }
        }
        if (invalid.isEmpty()) return null;
        return name + ": invalid phone(s) " + invalid + " (each must be 10 digits or 12 digits starting with 91)";
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: {
                String val = cell.getStringCellValue().trim();
                // Handle numbers stored as strings in scientific notation (e.g., "9.87654321E9")
                try {
                    double d = Double.parseDouble(val);
                    if (!Double.isInfinite(d) && !Double.isNaN(d)) {
                        return String.valueOf((long) d);
                    }
                } catch (NumberFormatException ignored) {}
                return val;
            }
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            case FORMULA: {
                try {
                    return String.valueOf((long) cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            }
            default: return "";
        }
    }
}
