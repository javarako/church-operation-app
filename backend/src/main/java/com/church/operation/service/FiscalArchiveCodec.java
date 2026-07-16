package com.church.operation.service;

import java.io.IOException;
import java.nio.file.Path;

public interface FiscalArchiveCodec {
    String write(Path output, char[] password, FiscalArchivePayload payload) throws IOException;

    Validated validate(Path archive, char[] password) throws IOException;

    record Validated(FiscalArchivePayload payload, String checksum) {
    }
}
