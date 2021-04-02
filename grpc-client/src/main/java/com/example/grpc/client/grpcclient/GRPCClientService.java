package com.example.grpc.client.grpcclient;

import com.example.grpc.server.grpcserver.MatrixRequest;
import com.example.grpc.server.grpcserver.MatrixResponse;
import com.example.grpc.server.grpcserver.MatrixServiceGrpc;
import com.example.grpc.server.grpcserver.MatrixServiceGrpc.MatrixServiceBlockingStub;
import com.example.grpc.shared.MatrixHelpers;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class GRPCClientService {
    private List<String> servers = Arrays.asList("localhost", "107.23.132.46");
    private List<ChannelStubCouple> channels = new ArrayList<ChannelStubCouple>();
    private BlockingQueue<Integer> channelIDs = new LinkedBlockingQueue<>(servers.size());

    public GRPCClientService() {
        this.initialise();
    }

    private void initialise() {
        // Set up channel and stub for each server
        for(String address : servers) {
            System.out.println("Init -> " + address);
            ManagedChannel channel = ManagedChannelBuilder.forAddress(address, 9090)
                .usePlaintext()
                .build();

            MatrixServiceBlockingStub stub = MatrixServiceGrpc.newBlockingStub(channel);

            ChannelStubCouple couple = new ChannelStubCouple(channel, stub);
            channels.add(couple);
            int index = channels.indexOf(couple);
            channelIDs.add(index);
        }

        System.out.println(channelIDs.size() + " stubs available!");
    }


    /**
     * Interface between REST and RPC handling scalability and (sometimes) load-balancing.
     * @param matrixOne
     * @param matrixTwo
     * @param deadline
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public int[][] multiplyMatrices(int[][] matrixOne, int[][] matrixTwo, long deadline) throws InterruptedException, ExecutionException {
        // Split matrices into blocks then run first operation which is timed.
        HashMap<String, int[][]> blocks = this.splitIntoBlocks(matrixOne, matrixTwo);

        int firstChannelIndex = this.getNumOfChannels(1)[0];
        ChannelStubCouple firstCouple = channels.get(firstChannelIndex);

        long startTime = System.nanoTime();

        // A3 = (A1 * A2) + (B1 * C2)
        CompletableFuture<int[][]> A1A2RpcCall = CompletableFuture.supplyAsync(() -> 
            this.multiplyBlock(blocks.get("A1"), blocks.get("A2"), firstCouple.getStub()));

        int[][] A1A2Res = A1A2RpcCall.get();

        long endTime = System.nanoTime();
        long footprint = endTime - startTime;

        // Operation is timed, number of servers required is calculated. Note: Addition calls are disregarded hence only 8 calls including the one before.
        final int numberOfCalls = 8;
        int numberOfServers = (int) Math.ceil(((float) footprint * (float) numberOfCalls) / (float) deadline);

        // Limit the number of servers incase that many aren't available.
        if(numberOfServers > channels.size()) {
            numberOfServers = channels.size();
        }

        // Retrieve a selection of servers that are stored in a queue.
        int[] selectedChannelIDs = this.getNumOfChannels(numberOfServers);

        // Create another queue of servers.
        BlockingQueue<ChannelStubCouple> multiplyQueue = new LinkedBlockingQueue<>(numberOfCalls);

        // Populate the new queue and loop until call amount is satisfied.
        int idx = 0;
        while(multiplyQueue.size() != numberOfCalls) {
            // Loop back if out of servers
            if(idx == selectedChannelIDs.length) {
                idx = 0;
            }

            int index = selectedChannelIDs[idx];
            ChannelStubCouple couple = channels.get(index);
            multiplyQueue.add(couple);
            idx += 1;
        }


        /**
         * MULTIPLY BLOCKS
         * 
         * Prepare calls to multiply individual blocks by another.
         */
        CompletableFuture<int[][]> B1C2Res = CompletableFuture.supplyAsync(() -> {
            try {
                return this.multiplyBlock(blocks.get("B1"), blocks.get("C2"), multiplyQueue.take().getStub());
            } catch(InterruptedException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // B3 = (A1 * B2) + (B1 * D2)
        CompletableFuture<int[][]> A1B2Res = CompletableFuture.supplyAsync(() -> {
            try {
                return this.multiplyBlock(blocks.get("A1"), blocks.get("B2"), multiplyQueue.take().getStub());
            } catch(InterruptedException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        CompletableFuture<int[][]> B1D2Res = CompletableFuture.supplyAsync(() -> {
            try {
                return this.multiplyBlock(blocks.get("B1"), blocks.get("D2"), multiplyQueue.take().getStub());
            } catch(InterruptedException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // C3 = (C1 * A2) + (D1 * C2)
        CompletableFuture<int[][]> C1A2Res = CompletableFuture.supplyAsync(() -> {
            try {
                return this.multiplyBlock(blocks.get("C1"), blocks.get("A2"), multiplyQueue.take().getStub());
            } catch(InterruptedException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        CompletableFuture<int[][]> D1C2Res = CompletableFuture.supplyAsync(() -> {
            try {
                return this.multiplyBlock(blocks.get("D1"), blocks.get("C2"), multiplyQueue.take().getStub());
            } catch(InterruptedException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // D3 = (C1 * B2) + (D1 * D2)
        CompletableFuture<int[][]> C1B2Res = CompletableFuture.supplyAsync(() -> {
            try {
                return this.multiplyBlock(blocks.get("C1"), blocks.get("B2"), multiplyQueue.take().getStub());
            } catch(InterruptedException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        CompletableFuture<int[][]> D1D2Res = CompletableFuture.supplyAsync(() -> {
            try {
                return this.multiplyBlock(blocks.get("D1"), blocks.get("D2"), multiplyQueue.take().getStub());
            } catch(InterruptedException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        /**
         * ADD BLOCKS
         * 
         * Call upon the multiplyBlock RPCs and add blocks...
         */

        // Select another set of servers for addition operations
        final int numberOfAdditionCalls = 4;
        int[] additionChannelIDs = this.getNumOfChannels(numberOfServers);
        BlockingQueue<ChannelStubCouple> additionQueue = new LinkedBlockingQueue<>(numberOfAdditionCalls);

        idx = 0;
        while(additionQueue.size() != numberOfAdditionCalls) {
            if(idx == additionChannelIDs.length) {
                idx = 0;
            }

            int index = additionChannelIDs[idx];
            ChannelStubCouple couple = channels.get(index);
            additionQueue.add(couple);
            idx += 1;
        }

        // Send operations to stubs

        // A3 = A1A2 + B1D2;
        CompletableFuture<int[][]> A3 = CompletableFuture.supplyAsync(() -> {
            try {
                return this.addBlock(A1A2Res, B1C2Res.get(), additionQueue.take().getStub());
            } catch(InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // B3 = A1B2 + B1D2;
        CompletableFuture<int[][]> B3 = CompletableFuture.supplyAsync(() -> {
            try {
                return this.addBlock(A1B2Res.get(), B1D2Res.get(), additionQueue.take().getStub());
            } catch(InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // C3 = C1A2 + D1C2;
        CompletableFuture<int[][]> C3 = CompletableFuture.supplyAsync(() -> {
            try {
                return this.addBlock(C1A2Res.get(), D1C2Res.get(), additionQueue.take().getStub());
            } catch(InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // D3 = C1B2 = D1D2;
        CompletableFuture<int[][]> D3 = CompletableFuture.supplyAsync(() -> {
            try {
                return this.addBlock(C1B2Res.get(), D1D2Res.get(), additionQueue.take().getStub());
            } catch(InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // Join blocks to form final matrix which is returned.
        int[][] resultMatrix = this.joinFromBlocks(A3.get(), B3.get(), C3.get(), D3.get());

        return resultMatrix;
    }


    private int[][] multiplyBlock(int[][] matrixOne, int[][] matrixTwo, MatrixServiceGrpc.MatrixServiceBlockingStub stub) {
        // Prepare rpc call which require matrices
        MatrixRequest request = this.buildMatrixRequest(matrixOne, matrixTwo);
    
        // Call upon procedure which provides a string...
        MatrixResponse blockAddResponse = stub.multiplyBlock(request);

        // Transform the string into a familar format (( 2d array of integers ))
        int[][] result = MatrixHelpers.deserializeMatrix(blockAddResponse.getMatrix());

        return result;
    }

    private int[][] addBlock(int[][] matrixOne, int[][] matrixTwo, MatrixServiceGrpc.MatrixServiceBlockingStub stub) {
        MatrixRequest request = this.buildMatrixRequest(matrixOne, matrixTwo);
        MatrixResponse blockAddResponse = stub.addBlock(request);

        int[][] result = MatrixHelpers.deserializeMatrix(blockAddResponse.getMatrix());

        return result;
    }

    /**
     * Takes two matrices and builds a request from them to use in an RPC.
     */
    private MatrixRequest buildMatrixRequest(int[][] matrixOne, int[][] matrixTwo) {
        // Matrices are sent as strings to remove complication of having to account for different dimensions
        String serializedMatrixOne = MatrixHelpers.serializeMatrix(matrixOne);
        String serializedMatrixTwo = MatrixHelpers.serializeMatrix(matrixTwo);

        MatrixRequest request = MatrixRequest.newBuilder()
            .setMatrixOne(serializedMatrixOne)
            .setMatrixTwo(serializedMatrixTwo)
            .build();

        return request;
    }

    private int[] getNumOfChannels(int n) throws InterruptedException {
        int[] IDs = new int[n];

        for(int i = 0; i < n; i++) {
            // Dequeue...
            IDs[i] = this.channelIDs.take();

            // Then requeue...
            this.channelIDs.add(IDs[i]);
        }

        return IDs;
    }

    /**
     * splitIntoBlocks and joinFromBlocks are functions adapted from material provided with Lab 1.
     */
    private HashMap<String, int[][]> splitIntoBlocks(int[][] matrixOne, int[][] matrixTwo) {
        HashMap<String, int[][]> blocks = new HashMap<>();

        final int MAX = matrixOne.length;
        int bSize = MAX / 2;

        int[][] A1 = new int[MAX][MAX];
        int[][] A2 = new int[MAX][MAX];
        int[][] B1 = new int[MAX][MAX];
        int[][] B2 = new int[MAX][MAX];
        int[][] C1 = new int[MAX][MAX];
        int[][] C2 = new int[MAX][MAX];
        int[][] D1 = new int[MAX][MAX];
        int[][] D2 = new int[MAX][MAX];

        for(int i = 0; i < bSize; i++) {
            for(int j = 0; j < bSize; j++) {
                A1[i][j] = matrixOne[i][j];
                A2[i][j] = matrixTwo[i][j];
            }
        }

        for(int i = 0; i < bSize; i++) {
            for(int j = bSize; j < MAX; j++) {
                B1[i][j - bSize] = matrixOne[i][j];
                B2[i][j - bSize] = matrixTwo[i][j];
            }
        }

        for(int i = bSize; i < MAX; i++) {
            for(int j = 0; j < bSize; j++) {
                C1[i - bSize][j] = matrixOne[i][j];
                C2[i - bSize][j] = matrixTwo[i][j];
            }
        }

        for(int i = bSize; i < MAX; i++) {
            for(int j = bSize; j < MAX; j++) {
                D1[i - bSize][j - bSize] = matrixOne[i][j];
                D2[i - bSize][j - bSize] = matrixTwo[i][j];
            }
        }

        blocks.put("A1", A1);
        blocks.put("A2", A2);
        blocks.put("B1", B1);
        blocks.put("B2", B2);
        blocks.put("C1", C1);
        blocks.put("C2", C2);
        blocks.put("D1", D1);
        blocks.put("D2", D2);

        return blocks;
    }

    private int[][] joinFromBlocks(int[][] A3, int[][] B3, int[][] C3, int[][] D3) {
        final int MAX = A3.length;
        int bSize = MAX / 2;

        int[][] matrix = new int[MAX][MAX];

        for(int i = 0; i < bSize; i++) {
            for(int j = 0; j < bSize; j++) {
                matrix[i][j] = A3[i][j];
            }
        }

        for(int i = 0; i < bSize; i++) {
            for(int j = bSize; j < MAX; j++) {
                matrix[i][j] = B3[i][j - bSize];
            }
        }

        for(int i = bSize; i < MAX; i++) {
            for(int j = 0; j < bSize; j++) {
                matrix[i][j] = C3[i - bSize][j];
            }
        }

        for(int i = bSize; i < MAX; i++) {
            for(int j = bSize; j < MAX; j++) {
                matrix[i][j] = D3[i - bSize][j - bSize];
            }
        }

        return matrix;
    }

    /**
     * Helpful abstract data type
     */
    private class ChannelStubCouple {
        private ManagedChannel channel;
        private MatrixServiceBlockingStub stub;
        public ChannelStubCouple(ManagedChannel cchannel, MatrixServiceBlockingStub sstub) {
            this.channel = cchannel;
            this.stub = sstub;
        }

        public ManagedChannel getChannel() {
            return this.channel;
        }

        public MatrixServiceBlockingStub getStub() {
            return this.stub;
        }
    }
}
