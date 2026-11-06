package cn.local.oneshotalarm;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** 统一限制导入/下载的实际字节数，不信任文件提供方声明的大小。 */
final class BoundedStreams {
    static void copy(InputStream input, OutputStream output, long limit, String message)
            throws IOException {
        if (input == null) throw new IOException("文件无法读取");
        byte[] buffer = new byte[65536];
        long total = 0;
        int size;
        while ((size = input.read(buffer)) != -1) {
            total += size;
            if (total > limit) throw new IOException(message);
            output.write(buffer, 0, size);
        }
    }

    static String text(InputStream input, long limit, String message) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output, limit, message);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
