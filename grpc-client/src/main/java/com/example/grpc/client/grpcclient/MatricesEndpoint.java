package com.example.grpc.client.grpcclient;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.grpc.shared.MatrixHelpers;
import com.example.grpc.shared.MatrixException;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

@RestController
public class MatricesEndpoint {    

	GRPCClientService grpcClientService;    
	@Autowired
    	public MatricesEndpoint(GRPCClientService grpcClientService) {
        	this.grpcClientService = grpcClientService;
    	}    

	@GetMapping("/test/multiply")
    	public String testmatrices() {
			int A[][] = { {1, 2, 3, 4}, 
			{5, 6, 7, 8}, 
			{9, 10, 11, 12},
			{13, 14, 15, 16}}; 

			int B[][] = { {1, 2, 3, 4}, 
				{5, 6, 7, 8}, 
				{9, 10, 11, 12},
				{13, 14, 15, 16}};

            int[][] resp = null;
            
            try {
                resp = grpcClientService.multiplyMatrices(A, B, 0);
            } catch(InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

        	return Arrays.deepToString(resp);
    	}

	@PostMapping("/matrix/multiply")
    	public String multiplyMatrices(@RequestParam("matrixOne") MultipartFile fileOne, @RequestParam("matrixTwo") MultipartFile fileTwo, @RequestParam("deadline") float deadline) {
            // Time for manual profiling
            long startTime = System.nanoTime();
            try {
                // Parse files as strings then check if they're valid and then transform them into a multi-dimensional array of integers.
                String matrixOneString = new String(fileOne.getBytes());
                String matrixTwoString = new String(fileTwo.getBytes());

                int[][] matrixOne = parseMatrixInput(matrixOneString);
                int[][] matrixTwo = parseMatrixInput(matrixTwoString);

                // Work out deadline and then work out using rpc.
                long longDeadline = (long) (deadline * 10_000_000_00L);
                //System.out.println("======> deadline => " + deadline + " ======= " + "longDeadline L=> " + longDeadline);
                int[][] finalMatrix = this.grpcClientService.multiplyMatrices(matrixOne, matrixTwo, longDeadline);
                long endTime = System.nanoTime();

                System.out.println("Total time taken => " + (endTime - startTime) + " deadline => " + longDeadline + " dimension => " + matrixOne.length);
                return MatrixHelpers.serializeMatrix(finalMatrix);
            } catch(MatrixException | IOException | InterruptedException | ExecutionException ex) {
                ex.printStackTrace();

                return ex.getMessage();
            }

    	}

    private static int[][] parseMatrixInput(String input) throws MatrixException {
        // Tokenize lines from input to later use as dimension.
        String[] rows = input.trim().split("\n");
        String[] columns = rows[0].trim().split(" ");

        // Various checks against dimension
        if(rows.length < 1 || columns.length < 1) {
            throw new MatrixException("Matrix Exception: Matrix does not have rows or perhaps columns.");
        }

        if(rows.length != columns.length) {
            throw new MatrixException("Matrix Exception: Square matrix not provided");
        }

        if(!MatrixHelpers.isAPowerOfTwo(rows.length)) {
            throw new MatrixException("Matrix Exception: Matrix dimension is not a power of two.");
        }

        // Should be ideal dimension
        int [][] matrix = new int[rows.length][columns.length];

        try {
            for(int i = 0; i < rows.length; i++) {
                // Tokenize horizontally
                String[] rowTokens = rows[i].trim().split(" ");

                // Check for a dodgy line
                if(rowTokens.length != columns.length) {
                    throw new MatrixException("Matrix Exception: Matrix row length does not conform to original dimension.");
                }

                for(int j = 0; j < rowTokens.length; j++) {
                    matrix[i][j] = Integer.parseInt(rowTokens[j]);
                }
            }
        } catch(ArrayIndexOutOfBoundsException | NumberFormatException ex) {
            throw new MatrixException("Matrix Exception: Could not parse number.", ex);
        }

        return matrix;
    }
}
