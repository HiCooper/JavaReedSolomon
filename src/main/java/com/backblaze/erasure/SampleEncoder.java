/**
 * Command-line program encodes one file using Reed-Solomon 4+2.
 *
 * Copyright 2015, Backblaze, Inc.
 */

package com.backblaze.erasure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Command-line program encodes one file using Reed-Solomon 4+2.
 *
 * The one argument should be a file name, say "foo.txt".  This program
 * will create six files in the same directory, breaking the input file
 * into four data shards, and two parity shards.  The output files are
 * called "foo.txt.0", "foo.txt.1", ..., and "foo.txt.5".  Numbers 4
 * and 5 are the parity shards.
 *
 * The data stored is the file size (four byte int), followed by the
 * contents of the file, and then padded to a multiple of four bytes
 * with zeros.  The padding is because all four data shards must be
 * the same size.
 */
public class SampleEncoder {

    /**
     * 数据分片数
     */
    public static final int DATA_SHARDS = 4;
    /**
     * 奇偶校验分片数
     */
    public static final int PARITY_SHARDS = 2;

    /**
     * 分片总数
     */
    public static final int TOTAL_SHARDS = DATA_SHARDS + PARITY_SHARDS;

    /**
     * 每个数据分片增加 1B 信息头，一共 1 * 4 B
     */
    public static final int BYTES_IN_INT = DATA_SHARDS;

    public static void main(String [] arguments) throws IOException {

        // Parse the command line
        if (arguments.length != 1) {
            System.out.println("Usage: SampleEncoder <fileName>");
            return;
        }
        final File inputFile = new File(arguments[0]);
        if (!inputFile.exists()) {
            System.out.println("Cannot read input file: " + inputFile);
            return;
        }

        // Get the size of the input file.  (Files bigger that
        // Integer.MAX_VALUE will fail here!) 最大 2G
        final int fileSize = (int) inputFile.length();

        // 计算每个数据分片大小.  (文件大小 + 4个数据分片头) 除以 4 向上取整
        final int storedSize = fileSize + BYTES_IN_INT;
        final int shardSize = (storedSize + DATA_SHARDS - 1) / DATA_SHARDS;

        // 创建一个 4 个数据分片大小的 buffer
        final int bufferSize = shardSize * DATA_SHARDS;
        final byte [] allBytes = new byte[bufferSize];

        // buffer前4个字节（4B）保存文件大小信息
        ByteBuffer.wrap(allBytes).putInt(fileSize);

        // 读入文件到 字节数组（allBytes）
        InputStream in = new FileInputStream(inputFile);
        int bytesRead = in.read(allBytes, BYTES_IN_INT, fileSize);
        if (bytesRead != fileSize) {
            throw new IOException("not enough bytes read");
        }
        in.close();

        // 创建二维字节数组，将 文件字节数组 （allBytes）copy到该数组（shards）
        byte [] [] shards = new byte [TOTAL_SHARDS] [shardSize];

        // Fill in the data shards，todo 仅有第一个数据分片包含了文件大小信息？
        for (int i = 0; i < DATA_SHARDS; i++) {
            System.arraycopy(allBytes, i * shardSize, shards[i], 0, shardSize);
        }

        // 使用 Reed-Solomon 算法计算 2 个奇偶校验分片.
        ReedSolomon reedSolomon = ReedSolomon.create(DATA_SHARDS, PARITY_SHARDS);
        reedSolomon.encodeParity(shards, 0, shardSize);

        // Write out the resulting files.
        for (int i = 0; i < TOTAL_SHARDS; i++) {
            File outputFile = new File(
                    inputFile.getParentFile(),
                    inputFile.getName() + "." + i);
            OutputStream out = new FileOutputStream(outputFile);
            out.write(shards[i]);
            out.close();
            System.out.println("wrote " + outputFile);
        }
    }
}
