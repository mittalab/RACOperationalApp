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

        ExcelReadResult(List<Student> students, List<String> validationErrors) {
            this.students = students;
            this.validationErrors = validationErrors;
            this.success = validationErrors.isEmpty();
        }
    }

    /**
     * @param useContactSheet true for standard two-sheet files (Result + Contact);
     *                        false for single-sheet files where col 3 holds the phone number.
     */
    public ExcelReadResult readAndValidate(File file, boolean useContactSheet) throws IOException {
        logger.info("Reading Excel file: {} (useContactSheet={})", file.getAbsolutePath(), useContactSheet);
        List<String> errors = new ArrayList<>();
        List<Student> students = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            if (useContactSheet) {
                readTwoSheetFormat(workbook, students, errors);
            } else {
                readSingleSheetFormat(workbook, students, errors);
            }

            logger.info("Read {} students, {} validation errors", students.size(), errors.size());
        }

        return new ExcelReadResult(students, errors);
    }

    private void readTwoSheetFormat(Workbook workbook, List<Student> students, List<String> errors) {
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
        Set<String> duplicateNames = new HashSet<>();

        Iterator<Row> contactRows = contactSheet.iterator();
        if (contactRows.hasNext()) contactRows.next(); // skip header

        while (contactRows.hasNext()) {
            Row row = contactRows.next();
            String name = getCellStringValue(row.getCell(0)).trim().toUpperCase();
            String phone = getCellStringValue(row.getCell(1)).trim();
            String rollNo = getCellStringValue(row.getCell(2)).trim();

            if (name.isEmpty()) continue;

            if (nameToPhone.containsKey(name)) {
                duplicateNames.add(name);
                nameToPhone.remove(name);
            } else if (!duplicateNames.contains(name)) {
                nameToPhone.put(name, phone);
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
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            default: return "";
        }
    }
}
