/*
 * Each Inode must include
 *      1. The length of the file
 *      2. The number of the file table entries that point to this inode
 *      3. A flag to indicate if used or unused
 *      
 *  Synchronization is maintained in FileTable
 */
public class Inode {
    private final static int iNodeSize = 32;       // fix to 32 bytes
    private final static int directSize = 11;      // # direct pointers
    private final int charSize = 2;                //Added to make code more readable
    private final int shortSize = 2;               //Added to make code more readable
    private final int intSize = 4;                 //Added to make code more readable

    public int length;            //4 bytes         file size in bytes
    public short count;           //2 bytes         # file-table entries pointing to this
    public short flag;            //2 bytes         0 = unused, 1 = used, ...
    public short direct[] = new short[directSize]; //22 bytes // direct pointers
    public short indirect;        //2 bytes        // a indirect pointer

    public Inode( ) {                                     // a default constructor
        length = 0;
        count = 0;
        flag = 1;
        for ( int i = 0; i < directSize; i++ )
        {
            direct[i] = -1;
        }
        indirect = -1;
    }

    public Inode( short iNumber )  // retrieving inode from disk
    {                      
        int block = 1 + iNumber / 16;
        byte[] buf = new byte[512];
        SysLib.rawread(block, buf);

        int ptr = iNumber % 16 * iNodeSize; //Used to navigate the file
        length = SysLib.bytes2int(buf, ptr);
        ptr += intSize; 

        count = SysLib.bytes2short(buf, ptr);
        ptr += shortSize;

        flag = SysLib.bytes2short(buf, ptr);
        ptr += shortSize;
        for(int i = 0; i < 11; i++)
        {
            direct[i] = SysLib.bytes2short(buf, ptr);
            ptr += shortSize; 
        }
        indirect = SysLib.bytes2short(buf, ptr);
        ptr += shortSize;
    }

    public void toDisk( short iNumber ) {                  // save to disk as the i-th inode
        byte[] buf = new byte[iNodeSize]; //Create an empty inode
        byte byteptr = 0;

        SysLib.int2bytes(length, buf, byteptr); // store length
        int nextPtr = byteptr + intSize;

        SysLib.short2bytes(count, buf, nextPtr); //store count
        nextPtr += shortSize;

        SysLib.short2bytes(flag, buf, nextPtr); // store flag
        nextPtr += shortSize;

        int directPtr = 0;
        for(directPtr = 0; directPtr < 11; directPtr++)
        {
            SysLib.short2bytes(direct[directPtr], buf, nextPtr);
            nextPtr += shortSize;
        }

        SysLib.short2bytes(indirect, buf, nextPtr);
        nextPtr += shortSize;

        byte[] blockBuf = new byte[512];

        directPtr = 1 + iNumber / 16;
        SysLib.rawread( directPtr, blockBuf );
        nextPtr = iNumber % 16 * iNodeSize;
        System.arraycopy(buf, 0, blockBuf, nextPtr, iNodeSize);
        SysLib.rawwrite(directPtr, blockBuf);
    }
    
    public int findBlock(int blockNum)
    {
        if( ( blockNum / 512 ) < 11 ) //Direct
        {
            return direct[ blockNum / 512 ];
        }else if(indirect < 0) 
        {
            return -1; //Invalid
        } else //get indirect
        {
            byte[] buf = new byte[512];
            SysLib.rawread(indirect, buf);
            int dataLocation = (blockNum / 512) - directSize;
            return SysLib.bytes2short(buf, dataLocation * shortSize);
        }

    }
    
    //Use Inode iNumber to get data
    public int acquireTarget(int blockNum, short iNumber)
    {
        int blockLoc = blockNum / 512;
        if(blockLoc < directSize)
        {
            if(direct[blockLoc] >= 0)
            {
                return -1;
            }
            if(blockLoc > 0 && direct[blockLoc - 1] == -1)
            {
                return -2;
            }
            else{
                direct[blockLoc] = iNumber;
                return 0;
            }

        }
        else if(indirect < 0)
        {
            return 12;
        } else //bring in indirect
        {
            byte[] buf = new byte[512];
            SysLib.rawread(indirect, buf);
            int indLoc = blockLoc - directSize;
            if(SysLib.bytes2short(buf, indLoc * shortSize) > 0)
            {
                SysLib.cerr("indexBlock, indirectNumber = " + indLoc + " contents = " + SysLib.bytes2short(buf, indLoc * 2) + "\n");
                return -1;
            } else{
                SysLib.short2bytes(iNumber, buf, indLoc * 2);
                SysLib.rawwrite(indirect, buf);
                return 0;
            }

        }
    }
    //Get the indirect block
    public boolean getIndirect(short index)
    {
        for(int i = 0; i < directSize; i++)
        {
            if(direct[i] == -1) //if there's a direct block, use that
            {
                return false;
            }
        }
        
        if(indirect != -1)
        {
            return false; //If it's already set, return false
        }
        
        indirect = index;
        byte[] buf = new byte[512];
        
        for(int i = 0; i < 256; i++)
        {
            SysLib.short2bytes((short)-1, buf, i * shortSize);
        }
        
        SysLib.rawwrite(index, buf);
        return true;
    }
    
    public byte[] freeIndirectBlock()
    {
        if(indirect >= 0)
        {
            byte[] buf = new byte[512];
            SysLib.rawread(indirect, buf);
            indirect = -1;
            return buf;
        }
        return null;
    }

}











