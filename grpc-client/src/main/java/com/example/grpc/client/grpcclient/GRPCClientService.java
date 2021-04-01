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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
public class GRPCClientService {

    ManagedChannel channel1;
    MatrixServiceBlockingStub stub1;
    public GRPCClientService() {
        System.out.println("-------------constructor----------");
        this.channel1 = ManagedChannelBuilder.forAddress("localhost", 9090)
            .usePlaintext()
            .build();

        this.stub1 = com.example.grpc.server.grpcserver.MatrixServiceGrpc.newBlockingStub(channel1);
    }

    public int[][] multiplyMatrices(int[][] matrixOne, int[][] matrixTwo, long deadline) throws InterruptedException, ExecutionException {
        HashMap<String, int[][]> blocks = this.splitIntoBlocks(matrixOne, matrixTwo);

        long startTime = System.nanoTime();

        // A3 = (A1 * A2) + (B1 * C2)
        CompletableFuture<int[][]> A1A2Res = CompletableFuture.supplyAsync(() -> 
            this.multiplyBlock(blocks.get("A1"), blocks.get("A2"), stub1));

        CompletableFuture<int[][]> B1C2Res = CompletableFuture.supplyAsync(() -> 
            this.multiplyBlock(blocks.get("B1"), blocks.get("C2"), stub1));

        // B3 = (A1 * B2) + (B1 * D2)
        CompletableFuture<int[][]> A1B2Res = CompletableFuture.supplyAsync(() -> 
            this.multiplyBlock(blocks.get("A1"), blocks.get("B2"), stub1));

        CompletableFuture<int[][]> B1D2Res = CompletableFuture.supplyAsync(() -> 
            this.multiplyBlock(blocks.get("B1"), blocks.get("D2"), stub1));

        // C3 = (C1 * A2) + (D1 * C2)
        CompletableFuture<int[][]> C1A2Res = CompletableFuture.supplyAsync(() -> 
            this.multiplyBlock(blocks.get("C1"), blocks.get("A2"), stub1));

        CompletableFuture<int[][]> D1C2Res = CompletableFuture.supplyAsync(() -> 
            this.multiplyBlock(blocks.get("D1"), blocks.get("C2"), stub1));

        // D3 = (C1 * B2) + (D1 * D2)
        CompletableFuture<int[][]> C1B2Res = CompletableFuture.supplyAsync(() -> 
            this.multiplyBlock(blocks.get("C1"), blocks.get("B2"), stub1));

        CompletableFuture<int[][]> D1D2Res = CompletableFuture.supplyAsync(() -> 
            this.multiplyBlock(blocks.get("D1"), blocks.get("D2"), stub1));

        // A3 = A1A2 + B1D2;
        CompletableFuture<int[][]> A3 = CompletableFuture.supplyAsync(() -> {
            try {
                return this.addBlock(A1A2Res.get(), B1C2Res.get(), stub1);
            } catch(InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // B3 = A1B2 + B1D2;
        CompletableFuture<int[][]> B3 = CompletableFuture.supplyAsync(() -> {
            try {
                return this.addBlock(A1B2Res.get(), B1D2Res.get(), stub1);
            } catch(InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // C3 = C1A2 + D1C2;
        CompletableFuture<int[][]> C3 = CompletableFuture.supplyAsync(() -> {
            try {
                return this.addBlock(C1A2Res.get(), D1C2Res.get(), stub1);
            } catch(InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        // D3 = C1B2 = D1D2;
        CompletableFuture<int[][]> D3 = CompletableFuture.supplyAsync(() -> {
            try {
                return this.addBlock(C1B2Res.get(), D1D2Res.get(), stub1);
            } catch(InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

            return null;
        });

        int[][] resultMatrix = this.joinFromBlocks(A3.get(), B3.get(), C3.get(), D3.get());
        System.out.println("finished");

        return resultMatrix;
    }


    private int[][] multiplyBlock(int[][] matrixOne, int[][] matrixTwo, MatrixServiceGrpc.MatrixServiceBlockingStub stub) {
        MatrixRequest request = this.buildMatrixRequest(matrixOne, matrixTwo);
        // TODO
        MatrixResponse blockAddResponse = stub.multiplyBlock(request);

        int[][] result = MatrixHelpers.deserializeMatrix(blockAddResponse.getMatrix());

        return result;
    }

    private int[][] addBlock(int[][] matrixOne, int[][] matrixTwo, MatrixServiceGrpc.MatrixServiceBlockingStub stub) {
        MatrixRequest request = this.buildMatrixRequest(matrixOne, matrixTwo);
        // TODO
        MatrixResponse blockAddResponse = stub.addBlock(request);

        int[][] result = MatrixHelpers.deserializeMatrix(blockAddResponse.getMatrix());

        return result;
    }

    /**
     * Takes two matrices and builds a request from them to use in an RPC.
     */
    private MatrixRequest buildMatrixRequest(int[][] matrixOne, int[][] matrixTwo) {
        String serializedMatrixOne = MatrixHelpers.serializeMatrix(matrixOne);
        String serializedMatrixTwo = MatrixHelpers.serializeMatrix(matrixTwo);

        MatrixRequest request = MatrixRequest.newBuilder()
            .setMatrixOne(serializedMatrixOne)
            .setMatrixTwo(serializedMatrixTwo)
            .build();

        return request;
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
}
