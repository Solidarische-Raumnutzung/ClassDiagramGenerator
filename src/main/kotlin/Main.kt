package de.frohnmeyerwds

import com.github.javaparser.ParserConfiguration
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import net.sourceforge.plantuml.SourceStringReader
import kotlin.io.path.*

fun main(args: Array<String>) {
    if (args.size !in 1..2) {
        println("Usage: Main <path-to-package> [skip-pkg]")
        return
    }

    val solver = CombinedTypeSolver()
    solver.add(ReflectionTypeSolver())

    val symSolver = JavaSymbolSolver(solver)
    StaticJavaParser.getParserConfiguration()
        .setSymbolResolver(symSolver)
        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)

    val units = Path(args[0])
        .walk(PathWalkOption.BREADTH_FIRST)
        .filter { it.extension == "java" }
        .map { StaticJavaParser.parse(it) }
        .toList()

    val classes = units.flatMap { it.findAll(ClassOrInterfaceDeclaration::class.java) }
    val byPackage = classes.groupBy { it.fullyQualifiedName.orElseThrow().substringBeforeLast('.').trimStart(args.getOrElse(1) { "" })}
    val types = classes.associateBy { it.asType() }
    val sb = StringBuilder()
    sb.appendLine("@startuml")
    sb.appendLine("!pragma layout elk")
    byPackage.forEach { (pkg, it) -> it.forEach {
        val elems = pkg.split('.').filter { it.isNotBlank() }
        elems.forEach { sb.appendLine("package $it { ") }
        if (it.isInterface) sb.append("interface ")
        else if (it.isAbstract) sb.append("abstract ")
        else sb.append("class ")
        sb.appendLine("\"${it.nameAsString}\" as ${it.diagramName} {")
        it.methods.filter { it.isPublic }.forEach {
            sb.append('+').appendLine(it.getDeclarationAsString(false, false))
        }
        sb.appendLine("}")
        elems.forEach { sb.appendLine("}") }
    } }
    classes.forEach {
        val isSpringManaged = it.annotations.any {
            it.nameAsString in setOf("Service", "Controller")
        }
        (it.extendedTypes + it.implementedTypes).mapNotNull { types[it] }.forEach { sup ->
            sb.append(sup.diagramName).append(" <|-- ").appendLine(it.diagramName)
        }
        if (isSpringManaged) {
            it.constructors
                .flatMap { it.parameters }
                .mapNotNull { types[it.type] }
                .forEach { inj ->
                    sb.append(inj.diagramName)
                        .append(" ..> ")
                        .append(it.diagramName)
                        .appendLine(" : injected")
                }
            it.fields
                .filter { it.annotations.any { it.nameAsString == "Autowired" } }
                .mapNotNull { types[it.elementType] }
                .forEach { inj ->
                    sb.append(inj.diagramName)
                        .append(" ..> ")
                        .append(it.diagramName)
                        .appendLine(" : injected")
                }
        }
    }
    sb.append("@enduml")
    System.setProperty("PLANTUML_LIMIT_SIZE", "16384")
    val reader = SourceStringReader(sb.toString())
    val out = Path("result.png")
    out.outputStream().use { reader.outputImage(it) }
    ProcessBuilder("gimp", out.absolutePathString()).inheritIO().start()
}

private fun String.trimStart(start: String) = if (startsWith(start)) substring(start.length) else this
private val TypeDeclaration<*>.diagramName get() = fullyQualifiedName.orElseThrow().replace('.', '_').replace('$', '_')
private fun ClassOrInterfaceDeclaration.asType() = ClassOrInterfaceType(nameAsString)