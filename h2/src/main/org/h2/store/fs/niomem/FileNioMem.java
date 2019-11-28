/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store.fs.niomem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import org.h2.store.fs.FakeFileChannel;
import org.h2.store.fs.FileBase;

/**
 * This class represents an in-memory file.
 */
class FileNioMem extends FileBase {

    /**
     * The file data.
     */
    FileNioMemData data;

    private final boolean readOnly;
    private long pos;

    FileNioMem(FileNioMemData data, boolean readOnly) {
        this.data = data;
        this.readOnly = readOnly;
    }

    @Override
    public long size() {
        return data.length();
    }

    @Override
    public FileChannel truncate(long newLength) throws IOException {
        // compatibility with JDK FileChannel#truncate
        if (readOnly) {
            throw new NonWritableChannelException();
        }
        if (data == null) {
            throw new ClosedChannelException();
        }
        if (newLength < size()) {
            data.touch(readOnly);
            pos = Math.min(pos, newLength);
            data.truncate(newLength);
        }
        return this;
    }

    @Override
    public FileChannel position(long newPos) {
        this.pos = (int) newPos;
        return this;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (data == null) {
            throw new ClosedChannelException();
        }
        int len = src.remaining();
        if (len == 0) {
            return 0;
        }
        data.touch(readOnly);
        // offset is 0 because we start writing from src.position()
        pos = data.readWrite(pos, src, 0, len, true);
        src.position(src.position() + len);
        return len;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (data == null) {
            throw new ClosedChannelException();
        }
        int len = dst.remaining();
        if (len == 0) {
            return 0;
        }
        long newPos = data.readWrite(pos, dst, dst.position(), len, false);
        len = (int) (newPos - pos);
        if (len <= 0) {
            return -1;
        }
        dst.position(dst.position() + len);
        pos = newPos;
        return len;
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        if (data == null) {
            throw new ClosedChannelException();
        }
        int len = dst.remaining();
        if (len == 0) {
            return 0;
        }
        long newPos;
        newPos = data.readWrite(position, dst, dst.position(), len, false);
        len = (int) (newPos - position);
        if (len <= 0) {
            return -1;
        }
        dst.position(dst.position() + len);
        return len;
    }

    @Override
    public long position() {
        return pos;
    }

    @Override
    public void implCloseChannel() throws IOException {
        pos = 0;
        data = null;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        // do nothing
    }

    @Override
    public synchronized FileLock tryLock(long position, long size,
            boolean shared) throws IOException {
        if (data == null) {
            throw new ClosedChannelException();
        }
        if (shared) {
            if (!data.lockShared()) {
                return null;
            }
        } else {
            if (!data.lockExclusive()) {
                return null;
            }
        }

        return new FileLock(FakeFileChannel.INSTANCE, position, size, shared) {

            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public void release() throws IOException {
                data.unlock();
            }
        };
    }

    @Override
    public String toString() {
        return data == null ? "<closed>" : data.getName();
    }

}