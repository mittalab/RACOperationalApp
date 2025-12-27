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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExcelReaderService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelReaderService.class);

    public List<Student> readStudentsFromExcel(File file) throws IOException {
        logger.info("Reading Excel file: {}", file.getAbsolutePath());
        List<Student> students = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            // Skip header row
            if (rowIterator.hasNext()) {
                rowIterator.next();
                logger.debug("Skipped header row");
            }

            int rowNum = 1;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowNum++;
                try {
                    String name = getCellStringValue(row.getCell(0));
                    String phoneNumber = getCellStringValue(row.getCell(1));
                    double marksObtained = row.getCell(2).getNumericCellValue();
                    String additionalDetails = getCellStringValue(row.getCell(3));

                    students.add(new Student(name, phoneNumber, marksObtained, additionalDetails, phoneNumber));
                    logger.trace("Read student from row {}: {}", rowNum, name);
                } catch (Exception e) {
                    logger.error("Could not read student data from row {}", rowNum, e);
                }
            }
            logger.info("Successfully read {} students from the Excel file", students.size());
        } catch (IOException e) {
            logger.error("Failed to read Excel file: {}", file.getAbsolutePath(), e);
            throw e;
        }
        return students;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            default:
                return "";
        }
    }
}
