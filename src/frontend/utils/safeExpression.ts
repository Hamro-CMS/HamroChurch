export type PrimitiveValue = number | string | boolean | null

type TokenType = "number" | "string" | "boolean" | "null" | "lparen" | "rparen" | "op" | "eof"

interface Token {
    type: TokenType
    value: string | number | boolean | null
}

const MAX_EXPRESSION_LENGTH = 4096
const MAX_PARSE_DEPTH = 64

const MULTI_CHAR_OPS = ["===", "!==", "**", "==", "!=", "<=", ">=", "&&", "||"]
const SINGLE_CHAR_OPS = new Set(["+", "-", "*", "/", "%", "<", ">", "!"])

function tokenize(input: string): Token[] {
    if (input.length > MAX_EXPRESSION_LENGTH) {
        throw new Error("Expression exceeds maximum length")
    }

    const tokens: Token[] = []
    let i = 0
    const len = input.length

    while (i < len) {
        const ch = input[i]

        if (ch === " " || ch === "\t" || ch === "\n" || ch === "\r") {
            i++
            continue
        }

        if (ch === "(") {
            tokens.push({ type: "lparen", value: "(" })
            i++
            continue
        }

        if (ch === ")") {
            tokens.push({ type: "rparen", value: ")" })
            i++
            continue
        }

        // Quoted string literal
        if (ch === '"' || ch === "'") {
            const quote = ch
            i++
            let str = ""
            let closed = false
            while (i < len) {
                const c = input[i]
                if (c === "\\") {
                    i++
                    if (i >= len) throw new Error("Unterminated escape sequence")
                    const esc = input[i]
                    if (esc === "n") str += "\n"
                    else if (esc === "r") str += "\r"
                    else if (esc === "t") str += "\t"
                    else str += esc
                    i++
                    continue
                }
                if (c === quote) {
                    closed = true
                    i++
                    break
                }
                str += c
                i++
            }
            if (!closed) throw new Error("Unterminated string literal")
            tokens.push({ type: "string", value: str })
            continue
        }

        // Number literal (e.g., 42, 3.14, .5, 1e-3)
        if ((ch >= "0" && ch <= "9") || (ch === "." && i + 1 < len && input[i + 1] >= "0" && input[i + 1] <= "9")) {
            const start = i
            let hasDot = false
            if (input[i] === ".") {
                hasDot = true
                i++
            }
            while (i < len && input[i] >= "0" && input[i] <= "9") i++
            if (!hasDot && i < len && input[i] === ".") {
                hasDot = true
                i++
                while (i < len && input[i] >= "0" && input[i] <= "9") i++
            }
            if (i < len && (input[i] === "e" || input[i] === "E")) {
                i++
                if (i < len && (input[i] === "+" || input[i] === "-")) i++
                const expStart = i
                while (i < len && input[i] >= "0" && input[i] <= "9") i++
                if (i === expStart) throw new Error("Invalid exponent in number literal")
            }
            const num = Number(input.slice(start, i))
            if (Number.isNaN(num)) throw new Error("Invalid number literal")
            tokens.push({ type: "number", value: num })
            continue
        }

        // Multi-character operators
        let matchedMulti = ""
        for (const op of MULTI_CHAR_OPS) {
            if (input.startsWith(op, i)) {
                matchedMulti = op
                break
            }
        }
        if (matchedMulti) {
            tokens.push({ type: "op", value: matchedMulti })
            i += matchedMulti.length
            continue
        }

        // Single-character operators
        if (SINGLE_CHAR_OPS.has(ch)) {
            tokens.push({ type: "op", value: ch })
            i++
            continue
        }

        // Only safe literal keywords (true, false, null) are permitted; arbitrary identifiers are rejected
        if ((ch >= "a" && ch <= "z") || (ch >= "A" && ch <= "Z") || ch === "_") {
            const start = i
            while (i < len && ((input[i] >= "a" && input[i] <= "z") || (input[i] >= "A" && input[i] <= "Z") || (input[i] >= "0" && input[i] <= "9") || input[i] === "_")) {
                i++
            }
            const ident = input.slice(start, i)
            if (ident === "true") tokens.push({ type: "boolean", value: true })
            else if (ident === "false") tokens.push({ type: "boolean", value: false })
            else if (ident === "null") tokens.push({ type: "null", value: null })
            else throw new Error(`Disallowed identifier in expression: ${ident}`)
            continue
        }

        throw new Error(`Unexpected character in expression: ${ch}`)
    }

    tokens.push({ type: "eof", value: null })
    return tokens
}

class SafeExpressionParser {
    private tokens: Token[]
    private pos = 0
    private depth = 0

    constructor(tokens: Token[]) {
        this.tokens = tokens
    }

    parse(): PrimitiveValue {
        if (this.peek().type === "eof") {
            throw new Error("Empty expression")
        }
        const result = this.parseLogicalOr()
        if (this.peek().type !== "eof") {
            throw new Error(`Unexpected token: ${String(this.peek().value)}`)
        }
        return result
    }

    private peek(): Token {
        return this.tokens[this.pos]
    }

    private consume(): Token {
        return this.tokens[this.pos++]
    }

    private matchOp(...ops: string[]): string | null {
        const tok = this.peek()
        if (tok.type === "op" && ops.includes(tok.value as string)) {
            this.pos++
            return tok.value as string
        }
        return null
    }

    private enterDepth() {
        this.depth++
        if (this.depth > MAX_PARSE_DEPTH) {
            throw new Error("Expression exceeds maximum nesting depth")
        }
    }

    private exitDepth() {
        this.depth--
    }

    private parseLogicalOr(): PrimitiveValue {
        let left = this.parseLogicalAnd()
        while (this.matchOp("||")) {
            const right = this.parseLogicalAnd()
            left = Boolean(left) || Boolean(right)
        }
        return left
    }

    private parseLogicalAnd(): PrimitiveValue {
        let left = this.parseEquality()
        while (this.matchOp("&&")) {
            const right = this.parseEquality()
            left = Boolean(left) && Boolean(right)
        }
        return left
    }

    private parseEquality(): PrimitiveValue {
        let left = this.parseComparison()
        let op: string | null
        while ((op = this.matchOp("===", "!==", "==", "!=")) !== null) {
            const right = this.parseComparison()
            if (op === "===") left = left === right
            else if (op === "!==") left = left !== right
            // eslint-disable-next-line eqeqeq
            else if (op === "==") left = left == right
            // eslint-disable-next-line eqeqeq
            else left = left != right
        }
        return left
    }

    private parseComparison(): PrimitiveValue {
        let left = this.parseAdditive()
        let op: string | null
        while ((op = this.matchOp("<", "<=", ">", ">=")) !== null) {
            const right = this.parseAdditive()
            const bothStrings = typeof left === "string" && typeof right === "string"
            const l = bothStrings ? (left as string) : Number(left)
            const r = bothStrings ? (right as string) : Number(right)
            if (op === "<") left = l < r
            else if (op === "<=") left = l <= r
            else if (op === ">") left = l > r
            else left = l >= r
        }
        return left
    }

    private parseAdditive(): PrimitiveValue {
        let left = this.parseMultiplicative()
        let op: string | null
        while ((op = this.matchOp("+", "-")) !== null) {
            const right = this.parseMultiplicative()
            if (op === "+") {
                if (typeof left === "string" || typeof right === "string") {
                    left = String(left ?? "") + String(right ?? "")
                } else {
                    left = Number(left) + Number(right)
                }
            } else {
                left = Number(left) - Number(right)
            }
        }
        return left
    }

    private parseMultiplicative(): PrimitiveValue {
        let left = this.parseUnary()
        let op: string | null
        while ((op = this.matchOp("*", "/", "%")) !== null) {
            const right = this.parseUnary()
            const l = Number(left)
            const r = Number(right)
            if (op === "*") left = l * r
            else if (op === "/") left = l / r
            else left = l % r
        }
        return left
    }

    private parseUnary(): PrimitiveValue {
        this.enterDepth()
        try {
            const op = this.matchOp("+", "-", "!")
            if (op === "+") return +Number(this.parseUnary())
            if (op === "-") return -Number(this.parseUnary())
            if (op === "!") return !this.parseUnary()
            return this.parsePower()
        } finally {
            this.exitDepth()
        }
    }

    private parsePower(): PrimitiveValue {
        const base = this.parsePrimary()
        if (this.matchOp("**")) {
            const exponent = this.parseUnary()
            return Number(base) ** Number(exponent)
        }
        return base
    }

    private parsePrimary(): PrimitiveValue {
        const tok = this.peek()

        if (tok.type === "number" || tok.type === "string" || tok.type === "boolean" || tok.type === "null") {
            this.consume()
            return tok.value
        }

        if (tok.type === "lparen") {
            this.consume()
            this.enterDepth()
            try {
                const expr = this.parseLogicalOr()
                if (this.peek().type !== "rparen") {
                    throw new Error("Missing closing parenthesis")
                }
                this.consume()
                return expr
            } finally {
                this.exitDepth()
            }
        }

        throw new Error(`Unexpected token: ${String(tok.value)}`)
    }
}

/**
 * Deterministically evaluates safe arithmetic and comparison expressions
 * without eval() or new Function().
 */
export function evaluateSafeExpression(expression: string): PrimitiveValue {
    const trimmed = String(expression ?? "").trim()
    if (!trimmed) {
        throw new Error("Empty expression")
    }
    const tokens = tokenize(trimmed)
    return new SafeExpressionParser(tokens).parse()
}
