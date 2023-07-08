import java.io.*;
import java.util.*;

public class CA {
    private static PrintWriter pw = new PrintWriter(System.out);
    private static PrintWriter pw1 = new PrintWriter(System.out);
    private static PrintWriter pw2 = new PrintWriter(System.out);
    private static int number_OF_instruction;
    private static int c;

    // Memory
    private static final int INSTRUCTION_MEMORY_SIZE = 1024;
    private static final int DATA_MEMORY_SIZE = 2048;
    private static final int[] instructionMemory = new int[INSTRUCTION_MEMORY_SIZE];
    private static final int[] dataMemory = new int[DATA_MEMORY_SIZE];
    private static int instruction = 0;

    //Pipeline stages
    private static final int FETCH_STAGE = 0;
    private static final int DECODE_STAGE = 1;
    private static int OPCODE = 0;
    private static int R1 = 0;
    private static int R2_OR_IMMEDIATE = 0;
    private static final int EXECUTE_STAGE = 2;
    private static final int[][] pipeline = {{FETCH_STAGE},{OPCODE,R1,R2_OR_IMMEDIATE},{EXECUTE_STAGE}};
    private static final int[][] t_pipeline = {{FETCH_STAGE},{OPCODE,R1,R2_OR_IMMEDIATE},{EXECUTE_STAGE}};

    //Registers
    private static final int NUM_REGISTERS = 64;
    private static final int[] registerFile = new int[NUM_REGISTERS];
    private static int pc = 0;
    private static final int[] status = new int [8];

    //Control Hazard
    private static boolean controlHazard = false;

    //Clock cycle
    private static int clockCycle = 1;

    public static void fetch(){
        if (clockCycle > 1 && pipeline[FETCH_STAGE][0] != -1)
            t_pipeline[FETCH_STAGE][0] = pipeline[FETCH_STAGE][0];
        if (clockCycle < calculateTotalClockCycles(number_OF_instruction)-1){
            instruction = instructionMemory[pc];
            pipeline[FETCH_STAGE][0] = instruction;
            pc++ ;
        }
        PrintFETCH();
    }

    public static void decode() {
        if (clockCycle > 2 && pipeline[DECODE_STAGE][0] != -1) {
            t_pipeline[DECODE_STAGE][0] = pipeline[DECODE_STAGE][0] ;
            t_pipeline[DECODE_STAGE][1] = pipeline[DECODE_STAGE][1] ;
            t_pipeline[DECODE_STAGE][2] = pipeline[DECODE_STAGE][2] ;
        }
        if (clockCycle > 1 && clockCycle < calculateTotalClockCycles(number_OF_instruction) && t_pipeline[FETCH_STAGE][0] != -1){
            instruction = t_pipeline[FETCH_STAGE][0];
            OPCODE = instruction >>> 12 & 0xF;
            R1 = instruction >>> 6 & 0x3F ;
            R2_OR_IMMEDIATE = fromTwosComplement(toTwosComplement(instruction & 0x3F , 6));
            pipeline[DECODE_STAGE][0] = OPCODE ;
            pipeline[DECODE_STAGE][1] = R1 ;
            pipeline[DECODE_STAGE][2] = R2_OR_IMMEDIATE ;
        }
        PrintDECODE();
    }

    public static void execute () {
        Arrays.fill(status,0);
        if (clockCycle > 2 && t_pipeline[DECODE_STAGE][0] != -1){
            int opcode = t_pipeline[DECODE_STAGE][0];
            int operandA = t_pipeline[DECODE_STAGE][1];
            int operandB = t_pipeline[DECODE_STAGE][2];
            int val = 0;

            int old_r = 0;
            int old_m = 0;

            if (opcode <= 10 && opcode != 4 && opcode != 7)
                old_r = registerFile[operandA];
            if (opcode == 11 )
                old_m = dataMemory[operandB];

            switch(opcode){
                case 0 : // ADD, R
                    status[3] = addBinaryStrings(toTwosComplement(registerFile[operandA],8),toTwosComplement(registerFile[operandB],8)).length() > 8 ? 1:0;
                    registerFile[operandA] = registerFile[operandA] + registerFile[operandB];
                    break;
                case 1 : // SUB, R
                    registerFile[operandA] = registerFile[operandA] - registerFile[operandB];
                    break;
                case 2 : // MUL, R
                    registerFile[operandA] = registerFile[operandA] * registerFile[operandB];
                    break;
                case 3 : // MOVI, I
                    registerFile[operandA] = operandB;
                    break;
                case 4 : // BEQZ, I
                    val = registerFile[operandA] == 0? (pc-2)+operandB : pc-2;
                    break;
                case 5 : // ANDI, I
                    registerFile[operandA] = registerFile[operandA] & operandB;
                    break;
                case 6 : // EOR, R
                    registerFile[operandA] = registerFile[operandA] ^ registerFile[operandB];
                    break;
                case 7 : // BR, R
                    val = Integer.parseInt(Integer.toBinaryString(registerFile[operandA]) +""+ Integer.toBinaryString(registerFile[operandB]),2);
                    break;
                case 8 : // SAL, I
                    registerFile[operandA] = registerFile[operandA] << operandB;
                    break;
                case 9 : // SAR, I
                    registerFile[operandA] = registerFile[operandA] >> operandB;
                    break;
                case 10 : // LDR, I
                    registerFile[operandA] = dataMemory[operandB];
                    break;
                case 11 : // STR, I
                    dataMemory[operandB] = registerFile[operandA];
                    break;
            }
            if (opcode <= 2 || opcode == 5 || opcode == 6 || opcode == 8 || opcode == 9){
                status[5] = (fromTwosComplement(toTwosComplement(registerFile[operandA],8)) < 0) ? 1 : 0;
                status[7] = (fromTwosComplement(toTwosComplement(registerFile[operandA],8)) == 0) ? 1 : 0;
            }

            if (opcode <= 1){
                status[4] = registerFile[operandA] == fromTwosComplement(toTwosComplement(registerFile[operandA],8)) ? 0:1;
                status[6] = status[4] ^ status[5];
            }

            if(opcode == 4 || opcode == 7) {
                val = fromTwosComplement(toTwosComplement(val,8));
                pipeline[EXECUTE_STAGE][0] = val;
            }
            else {
                registerFile[operandA] = fromTwosComplement(toTwosComplement(registerFile[operandA],8));
                pipeline[EXECUTE_STAGE][0] = registerFile[operandA];
            }

            if (opcode <= 10 && opcode != 4 && opcode != 7 && old_r != registerFile[operandA])
                pw1.println("R"+operandA+" = " +registerFile[operandA] );
            if (opcode == 11  && old_m != dataMemory[operandB])
                pw2.println("MEM[" + t_pipeline[DECODE_STAGE][2] +"]" + " = " +registerFile[operandA]);

            if(opcode == 4 || opcode == 7){
                if (val-(pc-2) == 1){
                    val++ ;
                    val = fromTwosComplement(toTwosComplement(val,8));
                    pc = val;
                    pipeline[EXECUTE_STAGE][0] = pc;
                    PrintEXCUTE();
                    pipeline[DECODE_STAGE][0] = pipeline[DECODE_STAGE][1] = pipeline[DECODE_STAGE][2] = -1 ;
                    t_pipeline[DECODE_STAGE][0] = t_pipeline[DECODE_STAGE][1] = t_pipeline[DECODE_STAGE][2] = -1 ;
                    return;
                }
                if (val < (pc-2) || val-(pc-2) > 1){
                    PrintEXCUTE();
                    pipeline[FETCH_STAGE][0] = -1 ;
                    t_pipeline[FETCH_STAGE][0] = -1 ;
                    pipeline[DECODE_STAGE][0] = pipeline[DECODE_STAGE][1] = pipeline[DECODE_STAGE][2] = -1 ;
                    t_pipeline[DECODE_STAGE][0] = t_pipeline[DECODE_STAGE][1] = t_pipeline[DECODE_STAGE][2] = -1 ;
                    c += (val - (pc-2) > 0 ? -(val-pc) : 0) ;
                    c += (val - (pc-2) < 0 ? -(val-pc) : 0) ;
                    pc = val;
                    return;
                }
                PrintEXCUTE();
                return;
            }
        }
        PrintEXCUTE();
    }

    public static void main (String [] args) throws Exception {

        readInstructionsFromFile("TestCases.txt");
        while (clockCycle <= calculateTotalClockCycles(number_OF_instruction)) {
            fetch();
            decode();
            execute();

            printUpdates();
            clockCycle++;
        }
        printContents();
    }

    public static void initialize() {
        Arrays.fill(instructionMemory, 0);
        Arrays.fill(dataMemory, 0);
        Arrays.fill(registerFile, 0);
    }

    public static int calculateTotalClockCycles(int n) {
        return 3 + ((n - 1) * 1) + c;
    }

    public static void PrintFETCH() {
        if (clockCycle < calculateTotalClockCycles(number_OF_instruction)-1)
            pw.println("FETCH: Instruction "+pipeline[FETCH_STAGE][0] );
    }

    public static void PrintDECODE() {
        if (clockCycle > 1 && clockCycle < calculateTotalClockCycles(number_OF_instruction)){
            if (pipeline[DECODE_STAGE][0] == -1){
                pw.println("DECODE: Skipped (Control Hazard)");
                return;
            }
            pw.print("DECODE: ");
            switch (pipeline[DECODE_STAGE][0]){
                case 0 : // ADD, R
                    pw.print("ADD ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print("R"+pipeline[DECODE_STAGE][2]);
                    break;
                case 1 : // SUB, R
                    pw.print("SUB ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print("R"+pipeline[DECODE_STAGE][2]);
                    break;
                case 2 : // MUL, R
                    pw.print("MUL ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print("R"+pipeline[DECODE_STAGE][2]);
                    break;
                case 3 : // MOVI, I
                    pw.print("MOVI ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print(pipeline[DECODE_STAGE][2]);
                    break;
                case 4 : // BEQZ, I
                    pw.print("BEQZ ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print(pipeline[DECODE_STAGE][2]);
                    break;
                case 5 : // ANDI, I
                    pw.print("ANDI ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print(pipeline[DECODE_STAGE][2]);
                    break;
                case 6 : // EOR, R
                    pw.print("EOR ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print("R"+pipeline[DECODE_STAGE][2]);
                    break;
                case 7 : // BR, R
                    pw.print("BR ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print("R"+pipeline[DECODE_STAGE][2]);
                    break;
                case 8 : // SAL, I
                    pw.print("SAL ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print(pipeline[DECODE_STAGE][2]);
                    break;
                case 9 : // SAR, I
                    pw.print("SAR ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print(pipeline[DECODE_STAGE][2]);
                    break;
                case 10 : // LDR, I
                    pw.print("LDR ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print(pipeline[DECODE_STAGE][2]);
                    break;
                case 11 : // STR, I
                    pw.print("STR ");
                    pw.print("R"+pipeline[DECODE_STAGE][1]+" ");
                    pw.print(pipeline[DECODE_STAGE][2]);
                    break;
            }
            pw.println();
        }
    }

    public static void PrintEXCUTE() {
        if (clockCycle > 2) {
            if (t_pipeline[DECODE_STAGE][0] == -1){
                pw.println("EXECUTE: Skipped (Control Hazard)");
                return;
            }
            switch(t_pipeline[DECODE_STAGE][0]){
                case 0 :
                    pw.println("EXECUTE: " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + "R" + t_pipeline[DECODE_STAGE][1] + " + " + "R" + t_pipeline[DECODE_STAGE][2] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 1 :
                    pw.println("EXECUTE: " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + "R" + t_pipeline[DECODE_STAGE][1] + " - " + "R" + t_pipeline[DECODE_STAGE][2] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 2 :
                    pw.println("EXECUTE: " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + "R" + t_pipeline[DECODE_STAGE][1] + " * " + "R" + t_pipeline[DECODE_STAGE][2] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 3 :
                    pw.println("EXECUTE: " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 4 :
                    pw.println("EXECUTE: " + "PC"  + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 5 :
                    pw.println("EXECUTE: " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + "R" + t_pipeline[DECODE_STAGE][1] + " & " + "R" + t_pipeline[DECODE_STAGE][2] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 6 :
                    pw.println("EXECUTE: " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + "R" + t_pipeline[DECODE_STAGE][1] + " ⊕ " + "R" + t_pipeline[DECODE_STAGE][2] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 7 :
                    pw.println("EXECUTE: " + "PC" + " = " + "R" + t_pipeline[DECODE_STAGE][1] + " || " + "R" + t_pipeline[DECODE_STAGE][2] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 8 :
                    pw.println("EXECUTE: " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + "R" + t_pipeline[DECODE_STAGE][1] + " << " + t_pipeline[DECODE_STAGE][2] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 9 :
                    pw.println("EXECUTE: " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + "R" + t_pipeline[DECODE_STAGE][1] + " >> " + t_pipeline[DECODE_STAGE][2] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 10 :
                    pw.println("EXECUTE: " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + "MEM[" + t_pipeline[DECODE_STAGE][2] +"]" + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
                case 11 :
                    pw.println("EXECUTE: " + "MEM[" + t_pipeline[DECODE_STAGE][2] +"]" + " = " + "R" + t_pipeline[DECODE_STAGE][1] + " = " + pipeline[EXECUTE_STAGE][0]);
                    break;
            }
        }
    }

    public static void printUpdates() {
        System.out.println("Clock Cycle: " + clockCycle);
        System.out.println("-----------------------------");

        System.out.println("Pipeline Stages:");
        pw.flush();
        System.out.println("-----------------------------");


        System.out.println("Register Updates:");
        pw1.flush();
        System.out.println("PC = " + pc);
        System.out.print("Status = ");
        for (int i : status)
            System.out.print(i);
        System.out.println();

        System.out.println("-----------------------------");

        System.out.println("Memory Updates:");
        pw2.flush();

        System.out.println();
        System.out.println("###############################");
        System.out.println("###############################");
        System.out.println();
    }

    public static void printContents() {
        System.out.println("Register Contents:");
        for (int i = 0; i < NUM_REGISTERS; i++)
            System.out.print("R" + i + " = " + registerFile[i]+", ");
        System.out.println();
        System.out.println();

        System.out.println("Memory Contents:");
        for (int i = 0; i < DATA_MEMORY_SIZE; i++)
            System.out.print("MEM[" + i + "] = " + dataMemory[i]+", ");
        System.out.println();
    }

    public static void readInstructionsFromFile(String filePath) throws IOException {
        List<String> instructions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                instructions.add(line);
            }
        } catch (FileNotFoundException e) {
            System.out.println("File not found: " + filePath);
        }
        for (int i=0 ;i< instructions.size() ; i++) {
            StringBuilder sb = new StringBuilder();
            String[] parts = instructions.get(i).split(" ");
            String s1 = parts[1];
            String s2 = parts[2];
            if (s1.charAt(0) == '$')
                s1 = s1.substring(1);
            if (s2.charAt(0) == '$')
                s2 = s2.substring(1);
            switch (parts[0]){
                case "ADD" :
                    sb.append("0");
                    break;
                case "SUB" :
                    sb.append("1");
                    break;
                case "MUL" :
                    sb.append("10");
                    break;
                case "MOVI" :
                    sb.append("11");
                    break;
                case "BEQZ" :
                    sb.append("100");
                    break;
                case "ANDI" :
                    sb.append("101");
                    break;
                case "EOR" :
                    sb.append("110");
                    break;
                case "BR" :
                    sb.append("111");
                    break;
                case "SAL" :
                    sb.append("1000");
                    break;
                case "SAR" :
                    sb.append("1001");
                    break;
                case "LDR" :
                    sb.append("1010");
                    break;
                case "STR" :
                    sb.append("1011");
                    break;
            }
            s1 = Integer.toBinaryString(Integer.parseInt(s1.substring(1)));
            while (s1.length() < 6)
                s1 = "0" + s1 ;
            if (s2.charAt(0) == 'R'){
                s2 = Integer.toBinaryString(Integer.parseInt(s2.substring(1)));
                while (s2.length() < 6)
                    s2 = "0" + s2 ;
            }
            else
                s2 = toTwosComplement(Integer.parseInt(s2), 6);
            sb.append(s1);
            sb.append(s2);
            instructionMemory[i] = Integer.parseInt(sb.toString(),2);
        }
        number_OF_instruction = instructions.size();
    }

    public static String toTwosComplement(int value ,int n) {
        if (value < -(Math.pow(2,n-1)+1) || value > Math.pow(2,n-1)-1) {
            value &= (n == 6) ? 0x3F : 0XFF;
            value = fromTwosComplement(Integer.toBinaryString(value));
        }

        if (value >= 0)
            return String.format("%"+n+"s", Integer.toBinaryString(value)).replace(' ', '0');
        else {
            int positiveValue = Math.abs(value);
            String binary = String.format("%"+n+"s", Integer.toBinaryString(positiveValue)).replace(' ', '0');
            StringBuilder complement = new StringBuilder();
            boolean foundOne = false;

            for (int i = binary.length() - 1; i >= 0; i--) {
                char bit = binary.charAt(i);
                if (!foundOne) {
                    if (bit == '1') {
                        foundOne = true;
                    }
                    complement.insert(0, bit);
                }
                else
                    complement.insert(0, bit == '0' ? '1' : '0');
            }
            return complement.toString();
        }
    }

    public static int fromTwosComplement(String binary) {
        if (binary.charAt(0) == '0') {
            return Integer.parseInt(binary, 2);
        }
        else {
            StringBuilder complement = new StringBuilder();
            boolean foundOne = false;

            for (int i = binary.length() - 1; i >= 0; i--) {
                char bit = binary.charAt(i);
                if (!foundOne) {
                    if (bit == '1') {
                        foundOne = true;
                    }
                    complement.insert(0, bit);
                } else
                    complement.insert(0, bit == '0' ? '1' : '0');
            }
            return -Integer.parseInt(complement.toString(), 2);
        }
    }

    public static String addBinaryStrings(String binary1, String binary2) {
        int maxLength = Math.max(binary1.length(), binary2.length());
        StringBuilder result = new StringBuilder();
        int carry = 0;
        for (int i = 0; i < maxLength; i++) {
            int digit1 = i < binary1.length() ? binary1.charAt(binary1.length() - 1 - i) - '0' : 0;
            int digit2 = i < binary2.length() ? binary2.charAt(binary2.length() - 1 - i) - '0' : 0;

            int sum = digit1 + digit2 + carry;
            result.insert(0, sum % 2);
            carry = sum / 2;
        }
        if (carry > 0) {
            result.insert(0, carry);
        }
        return result.toString();
    }
}
