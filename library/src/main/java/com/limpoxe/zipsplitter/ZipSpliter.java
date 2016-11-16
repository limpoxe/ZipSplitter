package com.limpoxe.zipsplitter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Created by cailiming on 16/11/5.
 */
public class ZipSpliter {

    private static final int LOCSIG = 0x4034b50, EXTSIG = 0x8074b50,
            CENSIG = 0x2014b50, ENDSIG = 0x6054b50;

    private static final int LOCHDR = 30, EXTHDR = 16, CENHDR = 46, ENDHDR = 22,
            LOCVER = 4, LOCFLG = 6, LOCHOW = 8, LOCTIM = 10, LOCCRC = 14,
            LOCSIZ = 18, LOCLEN = 22, LOCNAM = 26, LOCEXT = 28, EXTCRC = 4,
            EXTSIZ = 8, EXTLEN = 12, CENVEM = 4, CENVER = 6, CENFLG = 8,
            CENHOW = 10, CENTIM = 12, CENCRC = 16, CENSIZ = 20, CENLEN = 24,
            CENNAM = 28, CENEXT = 30, CENCOM = 32, CENDSK = 34, CENATT = 36,
            CENATX = 38, CENOFF = 42, ENDSUB = 8, ENDTOT = 10, ENDSIZ = 12,
            ENDOFF = 16, ENDCOM = 20;

    private static final int GPBF_UTF8_FLAG = 1 << 11;

    private static final int GPBF_ENCRYPTED_FLAG = 1 << 0;

    private static final int GPBF_UNSUPPORTED_MASK = GPBF_ENCRYPTED_FLAG;

    public static File split(String srcZipFile, String fileInSrcZipFile, String targetZipFile) {

        RandomAccessFile raf = null;
        EOF eof = null;
        CentralDir centralDir = null;
        FileEntry fileEntry = null;

        try {
            raf = new RandomAccessFile(srcZipFile, "r");
            long eocdOffset = findIndexOfEOF(raf);
            eof = readEocdRecord(raf, eocdOffset);
            centralDir = findCentralDir(raf, eof.centralDirOffset, eof.numberOfEntry, fileInSrcZipFile);
            fileEntry = readFileEntry(raf, centralDir.localHeaderRelOffset);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (fileEntry != null && centralDir != null && eof != null) {
            return writeZip(fileEntry, centralDir, eof, targetZipFile);
        }
        return null;
    }

    private static File writeZip(FileEntry fileEntry, CentralDir centralDir, EOF eof, String targetZipFile) {

        try {
            File dest = new File(targetZipFile);
            if (dest.exists()) {
                dest.delete();
            }

            FileOutputStream fileOutputStream = new FileOutputStream(dest);

            writeLocalFile(fileOutputStream, fileEntry);
            writeCentralDir(fileOutputStream, centralDir);
            writeEndOfCentralDirRecord(fileOutputStream, eof,
                    centralDir.size,//由于是单文件，因此核心目录的大小就是1个目录的大小
                    fileEntry.size//由于是单文件，因此核心目录起始位移大小就是1个文件数据的大小
            );

            fileOutputStream.close();

            return dest;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void writeLocalFile(FileOutputStream fileOutputStream, FileEntry fileEntry) throws IOException {
        fileOutputStream.write(fileEntry.fileEntryHeader);
        fileOutputStream.write(fileEntry.fileNameAndExtra);
        fileOutputStream.write(fileEntry.fileData);
        if (fileEntry.endOfFile != null) {
            fileOutputStream.write(fileEntry.endOfFile);
        }
    }

    private static void writeEndOfCentralDirRecord(FileOutputStream fileOutputStream, EOF eof,
                                                   long totalCentralDirSize, long offsetOfRelArchive) throws IOException {
        //修正该磁盘上所记录的核心目录数量
        int centralDirNumber = 1;
        byte[] centralDirNumberBytes = BytesUtil.shotToBytes(centralDirNumber);
        eof.endofRecord[ENDSUB] = centralDirNumberBytes[0];
        eof.endofRecord[ENDSUB + 1] = centralDirNumberBytes[1];

        //修正核心目录结构总数
        int totalCentralDirNumber = 1;
        byte[] totalCentralDirNumberBytes = BytesUtil.shotToBytes(totalCentralDirNumber);
        eof.endofRecord[ENDTOT] = totalCentralDirNumberBytes[0];
        eof.endofRecord[ENDTOT + 1] = totalCentralDirNumberBytes[1];

        //修正核心目录的大小
        byte[] totalCentralDirSizeBytes = BytesUtil.intToBytes(totalCentralDirSize);
        eof.endofRecord[ENDSIZ] = totalCentralDirSizeBytes[0];
        eof.endofRecord[ENDSIZ + 1] = totalCentralDirSizeBytes[1];
        eof.endofRecord[ENDSIZ + 2] = totalCentralDirSizeBytes[2];
        eof.endofRecord[ENDSIZ + 3] = totalCentralDirSizeBytes[3];

        //修正核心目录开始位置相对于archive开始的位移
        byte[] offsetOfRelArchiveBytes = BytesUtil.intToBytes(offsetOfRelArchive);
        eof.endofRecord[ENDOFF] = offsetOfRelArchiveBytes[0];
        eof.endofRecord[ENDOFF + 1] = offsetOfRelArchiveBytes[1];
        eof.endofRecord[ENDOFF + 2] = offsetOfRelArchiveBytes[2];
        eof.endofRecord[ENDOFF + 3] = offsetOfRelArchiveBytes[3];

        fileOutputStream.write(eof.endofRecord);

    }

    private static void writeCentralDir(FileOutputStream fileOutputStream, CentralDir centralDir) throws IOException {

        //修正本地文件header的相对位移
        long headerOffset = 0;//由于是单文件，因此文件一定是第一个，则headerOffset就是0
        byte[] headerOffsetBytes = BytesUtil.intToBytes(headerOffset);
        centralDir.centralDir[CENOFF] = headerOffsetBytes[0];
        centralDir.centralDir[CENOFF + 1] = headerOffsetBytes[1];
        centralDir.centralDir[CENOFF + 2] = headerOffsetBytes[2];
        centralDir.centralDir[CENOFF + 3] = headerOffsetBytes[3];

        fileOutputStream.write(centralDir.centralDir);
        fileOutputStream.write(centralDir.nameBytes);
        fileOutputStream.write(centralDir.extraAndCommentBytes);
    }

    private static FileEntry readFileEntry(RandomAccessFile randomAccessFile, long localHeaderRelOffset) throws IOException {

        //移到文件源数据结构的开头
        randomAccessFile.seek(localHeaderRelOffset);

        byte[] fileEntry = new byte[LOCHDR];
        randomAccessFile.readFully(fileEntry);

        int genFlag = BytesUtil.bytesToShot(new byte[] {
                fileEntry[LOCFLG],
                fileEntry[LOCFLG + 1]
        });

        int intCompressedSize = BytesUtil.bytesToInt(new byte[] {
                fileEntry[LOCSIZ],
                fileEntry[LOCSIZ + 1],
                fileEntry[LOCSIZ + 2],
                fileEntry[LOCSIZ + 3],
        });
        int fileNameLength = BytesUtil.bytesToShot(new byte[] {
                fileEntry[LOCNAM],
                fileEntry[LOCNAM + 1]
        });
        int extraLength = BytesUtil.bytesToShot(new byte[] {
                fileEntry[LOCEXT],
                fileEntry[LOCEXT + 1]
        });

        byte[] fileNameAndEtra = new byte[fileNameLength + extraLength];
        randomAccessFile.readFully(fileNameAndEtra);

        byte[] fileData = new byte[intCompressedSize];
        randomAccessFile.readFully(fileData);

        byte[] endOfFile = null;

        if (hasDataDescriptor(genFlag)) {
            endOfFile = new byte[EXTHDR];
            randomAccessFile.readFully(endOfFile);
        }

        return new FileEntry(fileEntry, fileNameAndEtra, fileData, endOfFile);
    }

    private static boolean hasDataDescriptor(int genFlag) {
        return (genFlag & 0x2000) == 0x2000;
    }

    private static CentralDir findCentralDir(RandomAccessFile raf, long centralDirOffset, long numEntries, String fileName) throws IOException {

        //移到第一个目录源数据
        raf.seek(centralDirOffset);

        //开始遍历目录源数据
        for (long i = 0; i < numEntries; ++i) {
            CentralDir centralDir = readCentralDir(raf);
            if (centralDir.localHeaderRelOffset >= centralDirOffset) {
                throw new IOException("Local file header offset is after central directory");
            }
            String entryName = centralDir.name;
            if (entryName.equals(fileName)) {
                return centralDir;
            }
        }
        return null;
    }

    private static long findIndexOfEOF(RandomAccessFile raf) throws IOException {
        long scanOffset = raf.length() - ENDHDR;
        if (scanOffset < 0) {
            throw new IOException("File too short to be a zip file: " + raf.length());
        }

        raf.seek(0);//移到文件开头

        final int headerMagic = Integer.reverseBytes(raf.readInt());
        if (headerMagic == ENDSIG) {
            throw new IOException("Empty zip archive not supported");
        }

        if (headerMagic != LOCSIG) {
            throw new IOException("Not a zip archive");
        }

        long stopOffset = scanOffset - 0x10000;//max length of comment in zip file
        if (stopOffset < 0) {
            stopOffset = 0;
        }

        int endSig = Integer.reverseBytes(ENDSIG);
        long eocdOffset;
        while (true) {
            raf.seek(scanOffset);
            if (raf.readInt() == endSig) {
                eocdOffset = scanOffset;
                break;
            }

            scanOffset--;
            if (scanOffset < stopOffset) {
                throw new IOException("End Of Central Directory signature not found");
            }
        }
        return eocdOffset;
    }

    private static EOF readEocdRecord(RandomAccessFile raf, long offset) throws IOException {
        //移动到目录结束标识开头
        raf.seek(offset);

        byte[] endofRecord = new byte[(int)(raf.length() - offset)];
        raf.readFully(endofRecord);

        final long numberOfCentralDirs = BytesUtil.bytesToShot(new byte[]{
                endofRecord[ENDTOT],
                endofRecord[ENDTOT + 1]
        });

        final long centralDirOffset = BytesUtil.bytesToInt(new byte[]{
                endofRecord[ENDOFF],
                endofRecord[ENDOFF + 1],
                endofRecord[ENDOFF + 2],
                endofRecord[ENDOFF + 3]
        });

        return new EOF(endofRecord, centralDirOffset, numberOfCentralDirs);
    }

    private static CentralDir readCentralDir(RandomAccessFile raf) throws IOException {

        byte[] centralDir = new byte[CENHDR];
        raf.readFully(centralDir);

        int sig = BytesUtil.bytesToInt(new byte[]{
                centralDir[0],
                centralDir[1],
                centralDir[2],
                centralDir[3]
        });
        if (sig != CENSIG) {
            throw new IOException("Bad central dir signal");
        }

        int gpbf = BytesUtil.bytesToShot(new byte[]{
                centralDir[CENFLG],
                centralDir[CENFLG + 1],
        });
        if ((gpbf & GPBF_UNSUPPORTED_MASK) != 0) {
            throw new IOException("Invalid General Purpose Bit Flag: " + gpbf);
        }

        long compressedSize = BytesUtil.bytesToInt(new byte[]{
                centralDir[CENSIZ],
                centralDir[CENSIZ + 1],
                centralDir[CENSIZ + 2],
                centralDir[CENSIZ + 3]
        });

        int nameLength = BytesUtil.bytesToShot(new byte[]{
                centralDir[CENNAM],
                centralDir[CENNAM + 1],
        });
        int extraLength = BytesUtil.bytesToShot(new byte[]{
                centralDir[CENEXT],
                centralDir[CENEXT + 1],

        });
        int commentLength = BytesUtil.bytesToShot(new byte[]{
                centralDir[CENCOM],
                centralDir[CENCOM + 1],
        });

        long localHeaderRelOffset = BytesUtil.bytesToInt(new byte[]{
                centralDir[CENOFF],
                centralDir[CENOFF + 1],
                centralDir[CENOFF + 2],
                centralDir[CENOFF + 3]
        });

        byte[] nameBytes = new byte[nameLength];
        raf.readFully(nameBytes, 0, nameBytes.length);
        if (BytesUtil.containsNulByte(nameBytes)) {
            throw new IOException("Filename contains NUL byte: " + Arrays.toString(nameBytes));
        }

        Charset charset = Charset.forName("UTF-8");
        if ((gpbf & GPBF_UTF8_FLAG) != 0) {
            charset = Charset.forName("UTF-8");
        }
        String name = new String(nameBytes, 0, nameBytes.length, charset);

        byte[] extraAndComment = new byte[extraLength + commentLength];
        raf.readFully(extraAndComment);

        return new CentralDir(centralDir, nameBytes, extraAndComment, compressedSize, localHeaderRelOffset, name);
    }

    static class EOF {
        byte[] endofRecord;
        long centralDirOffset;
        long numberOfEntry;
        public EOF(byte[] endofRecord, long centralDirOffset, long numberOfEntry) {
            this.endofRecord = endofRecord;
            this.centralDirOffset = centralDirOffset;
            this.numberOfEntry = numberOfEntry;
        }
    }

    static class CentralDir {
        byte[] centralDir;
        byte[] nameBytes;
        byte[] extraAndCommentBytes;
        long size;

        long compressedSize;
        long localHeaderRelOffset;
        String name;

        CentralDir(byte[] centralDir,
                   byte[] nameBytes,
                   byte[] extraAndCommentBytes,
                long compressedSize,
                long localHeaderRelOffset,
                String name) {
            this.centralDir = centralDir;
            this.nameBytes = nameBytes;
            this.extraAndCommentBytes = extraAndCommentBytes;
            this.compressedSize = compressedSize;
            this.localHeaderRelOffset = localHeaderRelOffset;
            this.name = name;

            this.size = centralDir.length + nameBytes.length + extraAndCommentBytes.length;
        }
    }

    static class FileEntry {
        byte[] fileEntryHeader;
        byte[] fileNameAndExtra;
        byte[] fileData;
        byte[] endOfFile;

        long size;

        FileEntry(byte[] fileEntryHeader,
                byte[] fileNameAndExtra,
                byte[] fileData,
                byte[] endOfFile) {
            this.fileEntryHeader = fileEntryHeader;
            this.fileNameAndExtra = fileNameAndExtra;
            this.fileData = fileData;
            this.endOfFile = endOfFile;
            size = fileEntryHeader.length + fileNameAndExtra.length + fileData.length;
            if (endOfFile != null) {
                size = size + endOfFile.length;
            }
        }
    }
}
