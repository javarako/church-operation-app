package com.church.operation.service;

import com.church.operation.dto.ArchiveCollectionManifest;
import com.church.operation.dto.ArchiveManifest;
import com.church.operation.util.ArchiveType;
import org.bson.Document;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.church.operation.entity.Offering;
import com.church.operation.entity.FinancialTransaction;
import com.church.operation.entity.Budget;

@Service
public class MongoFiscalArchiveCodec implements FiscalArchiveCodec {
    private final ArchivePackageService archivePackageService;
    private final MongoConverter converter;
    private final BsonStreamCodec bson = new BsonStreamCodec();

    public MongoFiscalArchiveCodec(ArchivePackageService archivePackageService, MongoConverter converter) {
        this.archivePackageService = archivePackageService;
        this.converter = converter;
    }

    @Override
    public String write(Path output, char[] password, FiscalArchivePayload payload) throws IOException {
        Path directory = Files.createTempDirectory(output.getParent(), "fiscal-entries-");
        try {
            Map<String, Path> entries = new LinkedHashMap<>();
            add(entries, directory, "offerings/data.bson", documents(payload.offerings()));
            add(entries, directory, "financialTransactions/income.bson", documents(payload.linkedIncome()));
            add(entries, directory, "financialTransactions/expenses.bson", documents(payload.expenses()));
            add(entries, directory, "budgets/data.bson", documents(payload.budgets()));
            add(entries, directory, "fiscal/metadata.bson", List.of(metadata(payload)));
            List<ArchiveCollectionManifest> manifests = entries.keySet().stream()
                .map(name -> new ArchiveCollectionManifest(collection(name), name, 0, 0, ""))
                .toList();
            archivePackageService.write(
                output, password,
                new ArchiveManifest(ArchivePackageService.FORMAT_VERSION, ArchiveType.FISCAL, manifests),
                entries
            );
            return sha256(output);
        } finally {
            deleteTree(directory);
        }
    }

    @Override
    public Validated validate(Path archive, char[] password) throws IOException {
        try (ArchivePackageService.ValidatedArchive validated = archivePackageService.validate(
            archive, password, ArchiveType.FISCAL
        )) {
            Map<String, Path> entries = validated.entries();
            Document metadata = read(entries, "fiscal/metadata.bson").stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Fiscal archive metadata is missing."));
            FiscalArchivePayload payload = new FiscalArchivePayload(
                metadata.getString("archiveId"), metadata.getInteger("fiscalYear"),
                java.time.LocalDate.parse(metadata.getString("startDate")),
                java.time.LocalDate.parse(metadata.getString("endDate")),
                convert(read(entries, "offerings/data.bson"), Offering.class),
                convert(read(entries, "financialTransactions/income.bson"), FinancialTransaction.class),
                convert(read(entries, "financialTransactions/expenses.bson"), FinancialTransaction.class),
                convert(read(entries, "budgets/data.bson"), Budget.class)
            );
            return new Validated(payload, sha256(archive));
        }
    }

    private List<Document> read(Map<String, Path> entries, String name) throws IOException {
        Path path = entries.get(name);
        if (path == null) {
            throw new IllegalArgumentException("Fiscal archive entry is missing: " + name);
        }
        try (var input = Files.newInputStream(path)) {
            return bson.read(input);
        }
    }

    private <T> List<T> convert(List<Document> documents, Class<T> type) {
        return documents.stream().map(document -> converter.read(type, document)).toList();
    }

    private List<Document> documents(List<?> values) {
        return values.stream().map(value -> {
            Document document = new Document();
            converter.write(value, document);
            return document;
        }).toList();
    }

    private Document metadata(FiscalArchivePayload payload) {
        return new Document("archiveId", payload.archiveId())
            .append("fiscalYear", payload.fiscalYear())
            .append("startDate", payload.startDate().toString())
            .append("endDate", payload.endDate().toString())
            .append("offeringIds", payload.offerings().stream().map(value -> value.getId()).toList())
            .append("linkedIncomeIds", payload.linkedIncome().stream().map(value -> value.getId()).toList())
            .append("expenseIds", payload.expenses().stream().map(value -> value.getId()).toList())
            .append("budgetIds", payload.budgets().stream().map(value -> value.getId()).toList());
    }

    private void add(Map<String, Path> entries, Path directory, String name, List<Document> documents) throws IOException {
        Path path = directory.resolve(name.replace('/', '-'));
        try (OutputStream output = Files.newOutputStream(path)) {
            bson.write(documents, output);
        }
        entries.put(name, path);
    }

    private String collection(String entryName) {
        return entryName.substring(0, entryName.indexOf('/'));
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) >= 0;) {
                    digest.update(buffer, 0, count);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
