/*
 * The superblock is disk block 0. 
 * It is to describe:
 *      1. The number of disk blocks
 *      2. The number of inodes
 *      3. The block number of the head block of the free list
 *      Notes:
 *          Each block is 512 bytes.
 *          totalBlocks default should be 1000.
 *          64 Inodes
 *          FreeList should start at 5. 
 *              Block 0 is super
 *              Block 1,2,3,4 are used for inodes. 
 */
public class Superblock
{
    private final int BLOCKSIZE = 512;
    private final int DEFAULT_IBLOCKS = 64; //Number of files
    private final int intSize = 4;
    private final int shortSize = 2;
    private final int InodeSize = 32;
    public int totalBlocks;
    public int InodeBlocks;
    public int freeList;
    public Superblock(int desiredBlocks)
    {
        byte[] buf = new byte[BLOCKSIZE];
        SysLib.rawread(0, buf); // read first block
        int ptr = 0;
        totalBlocks = SysLib.bytes2int(buf, ptr);
        ptr += intSize;
        InodeBlocks = SysLib.bytes2int(buf, ptr);
        ptr += intSize;
        freeList = SysLib.bytes2int(buf, ptr);
        if(totalBlocks != desiredBlocks || InodeBlocks <= 0 || freeList < 2)
        {
            totalBlocks = desiredBlocks;
            SysLib.cout("default format( "+ DEFAULT_IBLOCKS + " ) \n");
        }
        format();

    }

    void format()
    {
        format(64);
    }

    /*format
     * Creates new Inodes that don't contain any information
     * Clears all the blocks, syncs
     */
    void format(int numFiles)
    {
        InodeBlocks = numFiles;

        for(short i = 0; i < InodeBlocks; i++)
        {
            Inode toAdd = new Inode();
            toAdd.flag = 0;
            toAdd.toDisk(i);
        }

        freeList = 2 + InodeBlocks * InodeSize / BLOCKSIZE;

        for(int i = freeList; i < totalBlocks; i++)
        {
            byte[] toAdd = new byte[BLOCKSIZE];
            for(int j = 0; j < BLOCKSIZE; j++)
            {
                toAdd[j] = 0;
            }

            SysLib.int2bytes(i + 1, toAdd, 0); //+1 is added to maintain superblock
            SysLib.rawwrite(i, toAdd);
        }
        sync();
    }

    public void sync() //Writeback totalBlocks, inodeblocks, freelist to disk
    {
        byte[] buf = new byte[BLOCKSIZE];
        SysLib.int2bytes(totalBlocks, buf, 0);
        SysLib.int2bytes(InodeBlocks, buf, 4);
        SysLib.int2bytes(freeList, buf, 8);
        SysLib.rawwrite(0, buf);
        SysLib.cout("Superblock synchronized\n");
    }

    public int getFreeBlock()//dequeue top block on free list
    {
        int freeBlock = freeList;
        if(freeBlock != -1)
        {
            byte[] buf = new byte[BLOCKSIZE];
            SysLib.rawread(freeBlock, buf);
            freeList = SysLib.bytes2int(buf, 0);
            SysLib.int2bytes(0, buf, 0);
            SysLib.rawwrite(freeBlock, buf);
        }
        return freeBlock;
    }

    public boolean returnBlock(int blockNum) //Enguque a given block to the end of the free list
    {
        if(blockNum < 0) return false; //Invalid blocknum
        byte[] buf = new byte[BLOCKSIZE];
        for(int i = 0; i < BLOCKSIZE; i++)
        {
            buf[i] = 0;
        }
        SysLib.int2bytes(freeList, buf, 0);
        SysLib.rawwrite(blockNum, buf);
        freeList = blockNum;
        return true;
    }

}
