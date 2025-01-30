package edu.kit.hci.soli.cdg

import com.github.javaparser.ParserConfiguration
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
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
    val types = classes.associateBy { it.asType() }
    val sb = StringBuilder()
    sb.appendLine("@startuml")
    sb.appendLine("!pragma layout elk")
    classes.byPackage(args.getOrElse(1) { "" }) { pkg, it ->
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
    }
    classes.forEach {
        val isSpringManaged = it.annotations.any {
            it.nameAsString in setOf("Service", "Controller")
        }
        (it.extendedTypes + it.implementedTypes).mapNotNull { types[it] }.forEach { sup ->
            sb.append(sup.diagramName).append(" <|-- ").appendLine(it.diagramName)
        }
        val relevantFields = it.fields.filter { !it.isStatic }
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
            relevantFields
                .filter { it.annotations.any { it.nameAsString == "Autowired" } }
                .flatMap { it.variables }
                .mapNotNull { types[it.type] }
                .forEach { inj ->
                    sb.append(inj.diagramName)
                        .append(" ..> ")
                        .append(it.diagramName)
                        .appendLine(" : injected")
                }
        }
        val relevantVars = (if (isSpringManaged) relevantFields
                            else relevantFields.filter { it.annotations.none { it.nameAsString == "Autowired" } })
            .flatMap { it.variables }
        relevantVars.forEach { fld ->
                val t = types[fld.type] ?: return@forEach
                sb.append(t.diagramName)
                    .append(" <-- ")
                    .append(it.diagramName)
                    .appendLine(" : ${fld.nameAsString}")
            }
        val variants = mapOf(
            StaticJavaParser.parseClassOrInterfaceType("Map") to 2,
            StaticJavaParser.parseClassOrInterfaceType("List") to 1,
            StaticJavaParser.parseClassOrInterfaceType("Set") to 1
        )
        relevantVars.forEach { fld ->
            val ct = fld.type as? ClassOrInterfaceType ?: return@forEach
            val ot = variants[ClassOrInterfaceType(ct.scope.orElse(null), ct.name, null)] ?: return@forEach
            val args = ct.typeArguments.orElse(null) ?: return@forEach
            val t = types[args[ot - 1]] ?: return@forEach
            sb.append(t.diagramName)
                .append(" --o ")
                .append(it.diagramName)
                .appendLine(" : ${fld.nameAsString}")
        }
    }
    units.flatMap { it.findAll(EnumDeclaration::class.java) }.byPackage(args.getOrElse(1) { "" }) { pkg, it ->
        val elems = pkg.split('.').filter { it.isNotBlank() }
        elems.forEach { sb.appendLine("package $it { ") }
        sb.appendLine("enum \"${it.nameAsString}\" as ${it.diagramName} {")
        it.entries.forEach {
            sb.appendLine(it.nameAsString)
        }
        sb.appendLine("}")
        elems.forEach { sb.appendLine("}") }
    }
    sb.append("@enduml")
    System.setProperty("PLANTUML_LIMIT_SIZE", "16384")
    val reader = SourceStringReader(sb.toString())
    val out = Path("result.png")
    out.outputStream().use { reader.outputImage(it) }
//    println(sb)
//    ProcessBuilder("gimp", out.absolutePathString()).inheritIO().start()
}

private fun String.trimStart(start: String) = if (startsWith(start)) substring(start.length) else this
private val TypeDeclaration<*>.diagramName get() = fullyQualifiedName.orElseThrow().replace('.', '_').replace('$', '_')
private fun ClassOrInterfaceDeclaration.asType() = StaticJavaParser.parseClassOrInterfaceType(nameAsString)
private fun <T : TypeDeclaration<*>> List<T>.byPackage(pkg: String, action: (pkg: String, it: T) -> Unit) {
    groupBy { it.fullyQualifiedName.orElseThrow().substringBeforeLast('.').trimStart(pkg) }.forEach { (pkg, it) -> it.forEach {
        action(pkg, it)
    } }
}