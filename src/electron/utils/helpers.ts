import crypto from "crypto"

// clone objects
export function clone<T>(object: T): T {
    if (object === null || typeof object !== "object") return object

    try {
        return structuredClone(object)
    } catch {
        return object
    }
}

// a few keys might not be placed in the same order in JS object vs store file
export function checkIfMatching(a: any, b: any): boolean {
    try {
        if (!a || !b || typeof a !== "object" || typeof b !== "object") return false
        return JSON.stringify(Object.entries(a).sort()) === JSON.stringify(Object.entries(b).sort())
    } catch (err) {
        console.warn("Failed to compare store data:", err)
        return false
    }
}

// async wait (instead of timeouts)
export function wait(ms: number) {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve("ended")
        }, Number(ms))
    })
}

// wait until input value is true
export function waitUntilValueIsDefined(value: () => any, intervalTime = 50, timeoutValue = 5000) {
    return new Promise((resolve) => {
        let currentValue = value()
        if (currentValue) resolve(currentValue)

        const timeout = setTimeout(() => {
            exit()
            resolve(null)
        }, timeoutValue)

        const interval = setInterval(() => {
            currentValue = value()
            if (!currentValue) return

            exit()
            resolve(currentValue)
        }, intervalTime)

        function exit() {
            clearTimeout(timeout)
            clearInterval(interval)
        }
    })
}

export function getMachineId(): string {
    try {
        const { config } = require("../data/store")
        let id = config?.get ? config.get("anonymous_device_id") || config.get("machineId") : undefined
        if (id && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(id))) return id

        id = crypto.randomUUID()

        if (config?.set) {
            config.set("anonymous_device_id", id)
            config.set("machineId", id)
        }
        return id
    } catch (err) {
        console.warn("Could not get anonymous device ID from config store:", err)
        return crypto.randomUUID()
    }
}
