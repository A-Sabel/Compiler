package compiler.vm;

import java.util.ArrayList;
import java.util.List;

import compiler.codegen.Instruction;
import compiler.codegen.Instruction.Opcode;

public class InterpreterTest {
    public static void main(String[] args) {
        List<Instruction> prog = new ArrayList<>();

        // method add(int a, int b) { return a + b; }
        prog.add(Instruction.methodStart("add", "int"));
        prog.add(Instruction.methodDescriptor("add", "(II)I"));
        prog.add(Instruction.param("a"));
        prog.add(Instruction.param("b"));
        prog.add(Instruction.typedLoad(Opcode.ILOAD, "t0", "0"));
        prog.add(Instruction.typedLoad(Opcode.ILOAD, "t1", "1"));
        prog.add(Instruction.typedBinary(Opcode.IADD, "t2", "t0", "t1"));
        prog.add(Instruction.retValue("t2"));
        prog.add(Instruction.methodEnd("add"));

        // call add(2, 3)
        prog.add(Instruction.constPush("t3", "2", "ICONST_2"));
        prog.add(Instruction.arg("t3"));
        prog.add(Instruction.constPush("t4", "3", "ICONST_3"));
        prog.add(Instruction.arg("t4"));
        prog.add(Instruction.callWithDescriptor("t5", "add", "(II)I", 2));

        // return the result from the top-level program so the demo can print it
        prog.add(Instruction.retValue("t5"));

        // run
        Interpreter vm = new Interpreter();
        Object result = vm.execute(prog);
        System.out.println(result);
    }
}
