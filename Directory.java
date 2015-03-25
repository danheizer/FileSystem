public class Directory {
    private static int maxChars = 30; // max characters of each file name

    // Directory entries
    private int fsizes[];        // each element stores a different file size.
    private char fnames[][];    // each element stores a different file name.
    private final int intSize = 4;
    private final int charSize = 2;

    public Directory( int maxInumber ) { // directory constructor
        fsizes = new int[maxInumber];     // maxInumber = max files
        for ( int i = 0; i < maxInumber; i++ ) 
        {
            fsizes[i] = 0;                 // all file size initialized to 0
        }
        fnames = new char[maxInumber][maxChars];
        String root = "/";                // entry(inode) 0 is "/"
        fsizes[0] = root.length( );        // fsize[0] is the size of "/".
        root.getChars( 0, fsizes[0], fnames[0], 0 ); // fnames[0] includes "/"
    }

    public void bytes2directory( byte data[] ) {
        // assumes data[] received directory information from disk
        // initializes the Directory instance with this data[]

        int byteIndex = 0;
        for(int i = 0; i < fsizes.length; i++ )
        {
            fsizes[i] = SysLib.bytes2int(data, byteIndex);
            byteIndex += intSize; //int, so 4
        }
        byteIndex = 0;
        for(int i = 0; i < fnames.length; i++)
        {
            String fileName = new String(data, i, maxChars * charSize);
            fileName.getChars(0, fsizes[i], fnames[i], 0);
            byteIndex += maxChars * charSize; // char = 2, advance out of the space reserved for name
        }       
    }

    public byte[] directory2bytes( ) 
    {
        // converts and return Directory information into a plain byte array
        // this byte array will be written back to disk
        // note: only meaningfull directory information should be converted
        // into bytes.
        int byteIndex = 0;
        byte[] buf = new byte[fsizes.length * intSize + fnames.length * maxChars * charSize];
        for(int i = 0; i < fsizes.length; i++)
        {
            SysLib.int2bytes(fsizes[i], buf, byteIndex);
            byteIndex += intSize;
        }
        
        for(int i = 0; i < fnames.length; i++)
        {
            String fileName = new String(fnames[i], 0, fsizes[i]);
            byte[] charBuf = fileName.getBytes();
            System.arraycopy(charBuf, 0, buf, byteIndex, charBuf.length);
            byteIndex += maxChars * charSize;
        }
        
        return buf;
    }

    public short ialloc( String fileName ) 
    {
        // filename is the one of a file to be created.
        // allocates a new inode number for this filename
        
        for(short i = 1; i < fsizes.length; i++)
        {
            //Find first available index
            if(fsizes[i] == 0)
            {
                fsizes[i] = Math.min(fileName.length(), maxChars);
                fileName.getChars(0, fsizes[i], fnames[i], 0);
                return i;
            }
        }
        return (short)-1;
    }

    public boolean ifree( short iNumber ) 
    {

        // deallocates this inumber (inode number)
        // the corresponding file will be deleted.
        
        if(fsizes[iNumber] > 0)
        {
            fsizes[iNumber] = 0;
            return true;
        }else return false; //Couldn't find the file to be deleted
    }

    public short namei( String fileName ) 
    {
        // returns the inumber corresponding to this filename
        for(short i = 0; i < fsizes.length; i++)
        {
            String storedName = new String(fnames[i], 0, fsizes[i]);
            if(storedName.compareTo(fileName) == 0) //match
            {
                return i;
            }
        }
        return (short)-1;
    }

        
}