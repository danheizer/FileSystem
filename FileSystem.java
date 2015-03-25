public class FileSystem{    private final int SEEK_SET = 0;    private final int SEEK_CUR = 1;    private final int SEEK_END = 2;    private Superblock superblock;    private Directory directory;    private FileTable filetable; //Vector, where you can use int fd to find files    public FileSystem(int numberOfBlocks)    {        superblock = new Superblock(numberOfBlocks);        directory = new Directory(superblock.InodeBlocks);        filetable = new FileTable(directory);        FileTableEntry temp = open("/", "r");        int size = fsize(temp);        if(size > 0)        {            byte[] buf = new byte[size];            read(temp, buf);            directory.bytes2directory(buf);        }        close(temp);    }    public boolean format(int files)    {        superblock.format(files);        directory = new Directory(superblock.InodeBlocks);        filetable = new         FileTable(directory);        return true;    }    /* reads up to buffer.length bytes from the file indicated by fd, starting at the position currently pointed      * to by the seek pointer.      *      * If bytes remaining between the current seek pointer and the end of file are less than buffer.length,      * SysLib.read reads as many bytes as possible, putting them into the  beginning of buffer.      *      * It increments the seek pointer by the number of bytes to have been read.      *      * The return value is the number of bytes that have been read, or a negative value upon an error.     */    public int read(FileTableEntry toRead, byte[] buffer)    {        if(toRead.mode != "w" && toRead.mode != "a")        {            int bytesRead = 0;            int bufSize = buffer.length;            synchronized(toRead)            {                while(bufSize > 0 && toRead.seekPtr < fsize(toRead))                {                    int targetBlock = toRead.inode.findBlock(toRead.seekPtr);                    if(targetBlock == -1) //invalid block                    {                        break;                    }                    byte[] readBuf = new byte[512];                    //Read file into readbuf                    SysLib.rawread(targetBlock, readBuf);                    int skptr = toRead.seekPtr % 512;                    int positionOffset = 512 - skptr;                    int readLength = Math.min(Math.min(positionOffset, bufSize), fsize(toRead) - toRead.seekPtr);                    //Copy maxLength bytes from readbuf to param buffer, from seek pointer, to copyPos                    System.arraycopy(readBuf, skptr, buffer, bytesRead, readLength);                    toRead.seekPtr += readLength;                    bytesRead += readLength;                    bufSize -= readLength;                }                return bytesRead;            }        } else { //Return -1 if error            return -1;        }    }    public int write(FileTableEntry toWrite, byte buffer[])    {        if(toWrite.mode == "r") //a, w, w+ are all valid        {            return -1;        } else        {            synchronized(toWrite)            {                int  bytesWrote = 0;                int bufSize = buffer.length;                while(bufSize > 0)                {                    int targetBlock = toWrite.inode.findBlock(toWrite.seekPtr);                    if(targetBlock == -1)                    {                        short freeBlock = (short)superblock.getFreeBlock();                        int blockFlag = toWrite.inode.acquireTarget(toWrite.seekPtr, freeBlock);                        if(blockFlag == 12) //Indirect                        {                            short free = (short)superblock.getFreeBlock();                            if(!toWrite.inode.getIndirect(free)) {                                return -1;                            }                            if(toWrite.inode.acquireTarget(toWrite.seekPtr, freeBlock) != 0) {                                return -1;                            }                            targetBlock = freeBlock;                        } else if ( blockFlag == -1) //Error                        {                            SysLib.cerr("Error: FileSystem->Write(): Bad block \n");                        }                        else  {                            targetBlock = freeBlock;                        }                    }                    byte[] buf = new byte[512];                    if(SysLib.rawread(targetBlock,buf)==-1)                    {                        SysLib.cout("Error: FileSystem: write \n    rawread failure \n");                        System.exit(0);                    }                    int skptr = toWrite.seekPtr % 512;                    int positionOffset = 512 - skptr;                    int writeLength = Math.min(positionOffset, bufSize);                    System.arraycopy(buffer, bytesWrote, buf, skptr, writeLength);                    SysLib.rawwrite(targetBlock, buf);                    toWrite.seekPtr += writeLength;                    bytesWrote += writeLength;                    bufSize -= writeLength;                    //Update the seek pointer                    if(toWrite.seekPtr > toWrite.inode.length)                    {                        toWrite.inode.length = toWrite.seekPtr;                    }                }                toWrite.inode.toDisk(toWrite.iNumber);                return bytesWrote;            }        }    }    public int seek(FileTableEntry toSeek, int offset, int whence)    {        synchronized(toSeek)        {            if(whence == SEEK_SET)            {                if(offset < 0 || offset > fsize(toSeek))                 {                    SysLib.cerr("File System: SEEK SET: offset out of bounds \n");                    return -1; //Invalid offset                }                toSeek.seekPtr = offset;            }else if(whence == SEEK_CUR)            {                if(toSeek.seekPtr + offset < 0 || toSeek.seekPtr + offset > fsize(toSeek))                {                    SysLib.cerr("File System: SEEK CUR: offset out of bounds \n");                    return -1;                }                toSeek.seekPtr += offset;            } else if(whence == SEEK_END)            {                if(fsize(toSeek) + offset < 0 || fsize(toSeek) + offset > fsize(toSeek))                {                    SysLib.cerr("File System: SEEK END: offset out of bounds \n");                    return -1;                }                toSeek.seekPtr = fsize(toSeek) + offset;            }            return toSeek.seekPtr;        }    }    public boolean delete(String fileName)    {        FileTableEntry temp = open(fileName, "w");        short inum = temp.iNumber;        boolean iFreeSuccess = directory.ifree(inum);        boolean closeSuccess = close(temp);        boolean retVal = (iFreeSuccess && closeSuccess);        return retVal;    }    public int fsize(FileTableEntry fte)    {        {                          return fte.inode.length;        }    }    public void sync()     {        FileTableEntry toWrite = open("/", "w");        byte[] buf = this.directory.directory2bytes();        write(toWrite, buf);        close(toWrite);        superblock.sync();    }    public FileTableEntry open(String name, String mode)    {        FileTableEntry toOpen = filetable.falloc( name, mode );        if(!(mode == "w" && !clearNodes(toOpen)))         {            return toOpen;        }        else {            return null;        }    }    public boolean close(FileTableEntry toClose)    {        synchronized(toClose)        {            toClose.count--;            if(toClose.count > 0)            {                return true;            }        }        return filetable.ffree(toClose);    }    //Clear out a FileTableEntry    private boolean clearNodes(FileTableEntry toClear)    {        if(toClear.inode.count != 1)        {            return false;        } else        {            byte[] buf = toClear.inode.freeIndirectBlock();            if(buf != null) // got valid data            {                byte cpyByte = 0;                short cpyShort = 0;                while(cpyShort != -1)                {                    superblock.returnBlock(cpyShort);                    cpyShort = SysLib.bytes2short(buf, cpyByte);                }            }            int index = 0;            while(true)            {                Inode currNode = toClear.inode;                if(index >= 11)                {                    toClear.inode.toDisk(toClear.iNumber);                    return true;                }                if(toClear.inode.direct[index] != -1)                {                    superblock.returnBlock(toClear.inode.direct[index]); //Return the block                    toClear.inode.direct[index] = -1;                    //Mark unused                }                index++;            }        }    }}