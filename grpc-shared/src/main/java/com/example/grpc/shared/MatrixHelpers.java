package com.example.grpc.shared;

import java.util.Arrays;

/**
 * Hello world!
 *
 */
public class MatrixHelpers 
{
    /**
     * Helper function to determine if given integer is a power of 2.
     */
    public static boolean isAPowerOfTwo(int n) {
        return (int) (Math.ceil((Math.log(n) / Math.log(2)))) == (int) (Math.floor((Math.log(n) / Math.log(2))));
    }

    /**
     * Transforms a matrix into a form that can be used by RPC.
     */
    public static String serializeMatrix(int[][] matrix) {
        return Arrays.deepToString(matrix);
    }

    /**
     * A helper functions that would convert the response from an RPC to a matrix.
     */
    public static int[][] deserializeMatrix(String matrixString) {
        return stringToDeepArray(matrixString);
    }

    /**
     * Helper function from https://stackoverflow.com/a/22428926 to reverse deepToString
     */
    private static int[][] stringToDeepArray(String arrayString) {
        int row = 0;
        int col = 0;

        for(int i = 0; i < arrayString.length(); i++) {
            if(arrayString.charAt(i) == '[') {
                row += 1;
            }
        }

        row -= 1;

        for(int i = 0;; i++) {
            if(arrayString.charAt(i) == ',') {
                col++;
            }
            if(arrayString.charAt(i) == ']') {
                break;
            }
        }

        col += 1;

        int[][] parsedMatrix = new int[row][col];

        arrayString = arrayString.replaceAll("\\[", "").replaceAll("\\]", "");

        String[] tokens = arrayString.split(", ");
        int j = -1;
        for(int i = 0; i < tokens.length; i++) {
            if(i % col == 0) {
                // If modulus is 0 then new row on matrix...
                j ++;
            }

            parsedMatrix[j][i % col] = Integer.parseInt(tokens[i]);
        }

        return parsedMatrix;
    }
}
