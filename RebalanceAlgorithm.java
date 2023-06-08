import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RebalanceAlgorithm {

    /** N: total number of dstores
     *  R: active dstores
     *  F: total number of files*/
    public ArrayList<ArrayList<String>> allocateFiles(int N, int R, Set<FileManager> managedFileList) {

        Set<String> files = ConcurrentHashMap.newKeySet();

        for(FileManager file : managedFileList) {
            files.add(file.getFileName());
        }

        int F = managedFileList.size();

        int minFilesPerDstore = (int) Math.floor((double) (R * F) / N);
        int maxFileAmount = (int) Math.ceil((double) (R * F) / N);
        int totalFilesNeeded = R * F;

        // Add each dstore N as a new ArrayList entry
        ArrayList<ArrayList<Integer>> fileDistribution = new ArrayList<ArrayList<Integer>>();
        for (int i = 0; i < N; i++) {
            fileDistribution.add(new ArrayList<Integer>());
        }

        // Add each file f replicated over dstores N
        int currFile = 0;
        int totalFilesAdded = 1;
        int i = 0;
        while (totalFilesAdded <= totalFilesNeeded) {
            for (ArrayList<Integer> fileList : fileDistribution) {
                i++;

                if (totalFilesAdded > totalFilesNeeded) {
                    break;
                }

                if (currFile == F) {
                    currFile = 0;
                }

                fileList.add(currFile);
                if (i % R == 0) {
                    currFile++;
                }
                totalFilesAdded++;
            }
        }

        // Replace numbers in fileDistribution with file names from managedFileList
        ArrayList<ArrayList<String>> updatedFileDistribution = new ArrayList<ArrayList<String>>();
        for (ArrayList<Integer> fileList : fileDistribution) {
            ArrayList<String> updatedList = new ArrayList<String>();
            for (Integer fileNumber : fileList) {
                updatedList.add((String) files.toArray()[fileNumber]);
            }
            updatedFileDistribution.add(updatedList);
        }

        return updatedFileDistribution;
    }
}
