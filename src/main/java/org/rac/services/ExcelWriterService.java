package org.rac.services;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rac.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ExcelWriterService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelWriterService.class);

    public void writeStudentsToExcel(List<Student> students, File file) throws IOException {
        logger.info("Writing abort report to Excel file: {}", file.getAbsolutePath());
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fileOut = new FileOutputStream(file)) {
            Sheet sheet = workbook.createSheet("Sent Messages");

            // Create header row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Name");
            headerRow.createCell(1).setCellValue("Phone Number");
            logger.debug("Created header row in abort report");

            // Create data rows
            int rowNum = 1;
            for (Student student : students) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(student.getName());
                row.createCell(1).setCellValue(student.getPhoneNumber());
                logger.trace("Added student to abort report: {}", student.getName());
            }

            workbook.write(fileOut);
            logger.info("Successfully wrote {} students to the abort report", students.size());
        } catch (IOException e) {
            logger.error("Failed to write abort report to Excel file: {}", file.getAbsolutePath(), e);
            throw e;
        }
    }
}
