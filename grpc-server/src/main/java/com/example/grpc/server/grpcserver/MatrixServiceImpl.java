package com.example.grpc.server.grpcserver;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import com.example.grpc.shared.MatrixHelpers;

@GrpcService
public class MatrixServiceImpl extends MatrixServiceGrpc.MatrixServiceImplBase {
    @Override
    public void addBlock(MatrixRequest request, StreamObserver<MatrixResponse> responseObserver) {
        int[][] matrixOne = MatrixHelpers.deserializeMatrix(request.getMatrixOne());
        int[][] matrixTwo = MatrixHelpers.deserializeMatrix(request.getMatrixTwo());

        final int MAX = matrixOne.length;
        int[][] resultMatrix = new int[MAX][MAX];

        for(int i = 0; i < MAX; i++) {
            for(int j = 0; j < MAX; j++) {
                resultMatrix[i][j] = matrixOne[i][j] + matrixTwo[i][j];
            }
        }

        String serializedMatrix = MatrixHelpers.serializeMatrix(resultMatrix);
        MatrixResponse response = MatrixResponse.newBuilder().setMatrix(serializedMatrix).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void multiplyBlock(MatrixRequest request, StreamObserver<MatrixResponse> responseObserver) {
        int[][] matrixOne = MatrixHelpers.deserializeMatrix(request.getMatrixOne());
        int[][] matrixTwo = MatrixHelpers.deserializeMatrix(request.getMatrixTwo());

        final int MAX = matrixOne.length;
        int[][] resultMatrix = new int[MAX][MAX];

        final int bSize = MAX / 2;

        for(int i = 0; i < bSize; i++) {
            for(int j = 0; j < bSize; j++) {
                for(int k = 0; k < bSize; k++) {
                    resultMatrix[i][j] += (matrixOne[i][k] * matrixTwo[k][j]);
                }
            }
        }

        String serializedMatrix = MatrixHelpers.serializeMatrix(resultMatrix);
        MatrixResponse response = MatrixResponse.newBuilder().setMatrix(serializedMatrix).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
