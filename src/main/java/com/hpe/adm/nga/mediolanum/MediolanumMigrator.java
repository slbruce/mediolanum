package com.hpe.adm.nga.mediolanum;

import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.attachments.CreateAttachment;
import com.hpe.adm.nga.sdk.authentication.SimpleClientAuthentication;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.entities.ReleaseEntityList;
import com.hpe.adm.nga.sdk.entities.RequirementEntityList;
import com.hpe.adm.nga.sdk.entities.TestManualEntityList;
import com.hpe.adm.nga.sdk.model.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediolanumMigrator {

    private static final Pattern RELEASE_PATTERN = Pattern.compile("(\\d+)-.*");

    public static void main(String[] args) throws IOException {
        Octane octane = new Octane.Builder(new SimpleClientAuthentication("demo_api_oct1_74yv0g6wlv9ppsoyw83opjlkw", "-7c4b96ee56f2bf5C", "HPE_REST_API_TECH_PREVIEW"))
                .Server("http://myd-vm01614.hpeswlab.net:8080")
                .sharedSpace(1001)
                .workSpace(1016)
                .build();

//        Octane octane = new Octane.Builder(new SimpleClientAuthentication("mediolanum_51e0v18d2kn15um93ep6mx49m", ")a530cc23beba3fedP", "HPE_REST_API_TECH_PREVIEW"))
//                .Server("http://myd-vm18992.hpeswlab.net:25675")
//                .sharedSpace(1001)
//                .workSpace(1003)
//                .build();


        final Map<Long, TestManualEntityModel> almIdToTestMap = new HashMap<>();
        final TestManualEntityList testManualEntityList = octane.entityList(TestManualEntityList.class);
        final OctaneCollection<TestManualEntityModel> testManualEntityModels = testManualEntityList.get().addFields(TestManualEntityList.AvailableFields.TRACEIDUDF).execute();
        testManualEntityModels.forEach(testManualEntityModel -> almIdToTestMap.put(testManualEntityModel.getTraceIdUdf(), testManualEntityModel));

        final Map<String, ReleaseEntityModel> almIdToReleaseMap = new HashMap<>();
        final ReleaseEntityList releaseEntityList = octane.entityList(ReleaseEntityList.class);
        final OctaneCollection<ReleaseEntityModel> releaseEntityModels = releaseEntityList.get().addFields(ReleaseEntityList.AvailableFields.NAME).execute();
        releaseEntityModels.forEach(releaseEntityModel -> {
            final Matcher matcher = RELEASE_PATTERN.matcher(releaseEntityModel.getName());
            if (matcher.matches()) {
                almIdToReleaseMap.put(matcher.group(1) , releaseEntityModel);
            }
        });

        final RequirementEntityList requirementEntityList = octane.entityList(RequirementEntityList.class);


        FileInputStream inputStream = new FileInputStream(new File("C:\\Users\\brucesp\\OneDrive - Hewlett Packard Enterprise\\dev\\ALM Import2Octane (v0.9.1).xlsm"));
        Workbook workbook = new XSSFWorkbook(inputStream);
        final Sheet reqSheet = workbook.getSheet("REQS");
        final Iterator<Row> iterator = reqSheet.iterator();
        while (iterator.hasNext()) {
            final Row next = iterator.next();
            if (next.getRowNum() == 0) {
                continue;
            }


            final Cell cell = next.getCell(25);
            if (cell == null) {
                continue;
            }
            final String octaneId = ((Integer) ((Double) cell.getNumericCellValue()).intValue()).toString();
            final RequirementEntityModel requirementEntity = requirementEntityList.at(octaneId).get().execute();

            addLinkedTests(almIdToTestMap, next, requirementEntity);

            addLinkedReleases(almIdToReleaseMap, next, requirementEntity);

            requirementEntityList.update().entities(Collections.singletonList(requirementEntity)).execute();

            uploadAttachments(octane, next, requirementEntity);


        }

    }

    private static void addLinkedReleases(Map<String, ReleaseEntityModel> almIdToReleaseMap, Row next, RequirementEntityModel requirementEntity) {
        final Cell linkedReleaseCell = next.getCell(19);
        if (linkedReleaseCell != null) {
            final String[] split = linkedReleaseCell.getStringCellValue().split("/");
            if (split.length > 0) {
                final Collection<ReleaseEntityModel> releasesToAddToRequirements = new HashSet<>();
                for (String almReq : split) {
                    if (!almReq.isEmpty()) {
                        final ReleaseEntityModel releaseEntityModel = almIdToReleaseMap.get(almReq);
                        releasesToAddToRequirements.add(releaseEntityModel);
                    }
                }

                requirementEntity.setRelease(releasesToAddToRequirements);
            }
        }
    }

    private static void addLinkedTests(Map<Long, TestManualEntityModel> almIdToTestMap, Row next, RequirementEntityModel requirementEntity) {
        Cell linkedTestCell = next.getCell(4);
        if (linkedTestCell != null) {
            final String linkedTests = linkedTestCell.getStringCellValue();
            final String[] tests = linkedTests.split(";");
            final Collection<TestManualEntityModel> testsToAddToRequirements = new HashSet<>(tests.length);
            for (String test : tests) {
                if (!test.isEmpty()) {
                    final TestManualEntityModel testManualEntityModel = almIdToTestMap.get(Long.parseLong(test));
                    testsToAddToRequirements.add(testManualEntityModel);
                }
            }

            requirementEntity.setTest(testsToAddToRequirements);

        }
    }

    private static void uploadAttachments(Octane octane, Row next, RequirementEntityModel requirementEntity) throws FileNotFoundException {
        // attachments
        Cell attachmentCell = next.getCell(3);
        if (attachmentCell != null) {

            final String attachmentCellStringCellValue = attachmentCell.getStringCellValue();
            final String[] attachmentSplit = attachmentCellStringCellValue.split(">");
            for (String attachmentFileName : attachmentSplit) {
                File attachmentFile = new File(attachmentFileName);

                EntityModel attachmentEntityModel = new EntityModel();
                EntityModel owner_requirementModel = new EntityModel();
                owner_requirementModel.setValue(new StringFieldModel("id", requirementEntity.getId()));
                owner_requirementModel.setValue(new StringFieldModel("type", "requirement"));
                attachmentEntityModel.setValue(new ReferenceFieldModel("owner_requirement", owner_requirementModel));
                attachmentEntityModel.setValue(new StringFieldModel("name", attachmentFile.getName()));

                final String contentType = new MimetypesFileTypeMap().getContentType(attachmentFile);
                final CreateAttachment attachment = octane.attachmentList().create().attachment(attachmentEntityModel, new FileInputStream(attachmentFile), contentType, attachmentFile.getName());
                attachment.execute();
            }
        }
    }

}
