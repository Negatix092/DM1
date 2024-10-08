import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Coordinator {

    private List<String> inputData;
    private Map<String, List<String>> resultsMap;
    private int chunkSize;
    private String filePath;
    private String outputFilePath = "/Users/alexperez/Documents/GitHub/DM1/M_3/MapReduce/src/Files/Chunks/";

    public Coordinator(int chunkSize, String filePath) {
        this.resultsMap = new TreeMap<>();
        this.chunkSize = 32 * 1000 * 1000;
        this.filePath = filePath;
    }

    public void startProcessing() throws InterruptedException, ExecutionException, IOException {
        deleteDirectory(new File(outputFilePath));
        System.out.println("\u001B[34mInfo: Carpeta 'Chunks' borrada. Procesando...\u001B[0m");
        Thread.sleep(5000); // Espera 5 segundos para asegurar que el directorio ha sido borrado
    }

    private boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!deleteDirectory(file)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public List<String> split() {
        List<String> chunks = new ArrayList<>();
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filePath))) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            int chunkCount = 0;

            while ((bytesRead = bis.read(buffer)) != -1) {
                String chunkFileName = outputFilePath + "chunk_" + chunkCount + ".txt";
                try (FileOutputStream fos = new FileOutputStream(chunkFileName)) {
                    fos.write(buffer, 0, bytesRead);
                    chunks.add(chunkFileName);
                    chunkCount++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return chunks;
    }

    public void executeMap(List<String> chunks) {
        int numMapNodes = 40; // Ahora son 40 nodos map, uno por cada chunk
        ExecutorService mapPool = Executors.newFixedThreadPool(numMapNodes);
        List<Future<Map<String, List<Integer>>>> mapFutures = new ArrayList<>();
    
        // Cada nodo map procesará un chunk
        for (int i = 0; i < numMapNodes; i++) {
            List<String> chunkSubset = chunks.subList(i, i + 1); // Un chunk por nodo
            MapNode mapNode = new MapNode(chunkSubset, outputFilePath + "map_" + i + ".txt");
            Future<Map<String, List<Integer>>> future = mapPool.submit(mapNode);
            mapFutures.add(future);
        }
    
        mapPool.shutdown();
        while (!mapPool.isTerminated()) {}
    
        System.out.println("Fase Map completada.");
    }
    

    public void executeShuffle() {
        int numShuffleNodes = 40; // 40 nodos shuffle, uno por cada archivo map
        ExecutorService shufflePool = Executors.newFixedThreadPool(numShuffleNodes);
    
        List<String> mapFiles = new ArrayList<>();
        for (int i = 0; i < numShuffleNodes; i++) {
            mapFiles.add(outputFilePath + "map_" + i + ".txt"); // Archivos generados por los 40 nodos map
        }
    
        List<Future<Map<String, List<Integer>>>> shuffleFutures = new ArrayList<>();
        for (int i = 0; i < numShuffleNodes; i++) {
            // Cada nodo shuffle procesará un archivo generado por los mappers
            ShuffleNode shuffleNode = new ShuffleNode(mapFiles.subList(i, i + 1), outputFilePath + "shuffle_" + i + ".txt");
            Future<Map<String, List<Integer>>> future = shufflePool.submit(shuffleNode);
            shuffleFutures.add(future);
        }
    
        shufflePool.shutdown();
        while (!shufflePool.isTerminated()) {}
    
        System.out.println("Fase Shuffle completada.");
    }
    

    public void executeReduce() {
        int numReduceNodes = 2; // Dos nodos reduce
        ExecutorService reducePool = Executors.newFixedThreadPool(numReduceNodes);
    
        List<String> allShuffleFiles = new ArrayList<>();
        for (int i = 0; i < 40; i++) { // Todos los archivos shuffle generados
            allShuffleFiles.add(outputFilePath + "shuffle_" + i + ".txt");
        }
    
        // Dividir los 40 archivos shuffle entre dos nodos reduce
        List<String> firstHalfShuffleFiles = allShuffleFiles.subList(0, 20);
        List<String> secondHalfShuffleFiles = allShuffleFiles.subList(20, 40);
    
        // Reducer 1 trabaja con los primeros 20 archivos shuffle
        Future<Map<String, Integer>> reduceFuture1 = reducePool.submit(new ReduceNode(firstHalfShuffleFiles, outputFilePath + "reduce_1.txt"));
        // Reducer 2 trabaja con los últimos 20 archivos shuffle
        Future<Map<String, Integer>> reduceFuture2 = reducePool.submit(new ReduceNode(secondHalfShuffleFiles, outputFilePath + "reduce_2.txt"));
    
        reducePool.shutdown();
        while (!reducePool.isTerminated()) {}
    
        try {
            Map<String, Integer> finalResults1 = reduceFuture1.get();
            Map<String, Integer> finalResults2 = reduceFuture2.get();
    
            // Guardar los resultados finales
            try (FileWriter writer = new FileWriter(outputFilePath + "final_reduce_1.txt")) {
                for (Map.Entry<String, Integer> entry : finalResults1.entrySet()) {
                    writer.write("(" + entry.getKey() + ", " + entry.getValue() + ")\n");
                }
            }
    
            try (FileWriter writer = new FileWriter(outputFilePath + "final_reduce_2.txt")) {
                for (Map.Entry<String, Integer> entry : finalResults2.entrySet()) {
                    writer.write("(" + entry.getKey() + ", " + entry.getValue() + ")\n");
                }
            }
    
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    
        System.out.println("Fase Reduce completada. Resultados finales guardados.");
    }    
    
    
    

}
