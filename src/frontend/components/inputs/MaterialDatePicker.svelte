<script lang="ts">
    import { createEventDispatcher } from "svelte"
    import { BS_MONTHS, bsToGregorian, fromNepaliDigits, getBsDateParts, getBsMonthLength, toNepaliDigits } from "../../../common/nepali"
    import { dictionary } from "../../stores"
    import { translateText } from "../../utils/language"
    import MaterialTimePicker from "./MaterialTimePicker.svelte"

    export let label: string
    export let value: string // expected format: "YYYY-MM-DD"
    export let defaultValue: string | null = null
    export let displayMode: "bs" | "ad" = "ad"

    export let id = ""
    export let title = ""

    export let disabled = false
    export let autofocus = false

    const dispatch = createEventDispatcher()

    let bsYear = ""
    let bsMonth = 1
    let bsDay = ""

    $: syncBsFromValue(value)

    function parseIsoDate(isoDate: string) {
        const [year, month, day] = String(isoDate || "")
            .split("-")
            .map((entry) => Number(entry))
        if (!year || !month || !day) return null
        return new Date(year, month - 1, day)
    }

    function toIsoDate(date: Date) {
        const year = date.getFullYear().toString().padStart(4, "0")
        const month = (date.getMonth() + 1).toString().padStart(2, "0")
        const day = date.getDate().toString().padStart(2, "0")
        return `${year}-${month}-${day}`
    }

    function normalizeNumberInput(raw: string) {
        return fromNepaliDigits(raw).replace(/[^0-9]/g, "")
    }

    function syncBsFromValue(isoDate: string) {
        const parsed = parseIsoDate(isoDate)
        if (!parsed) return

        const parts = getBsDateParts(parsed)
        bsYear = String(parts.year)
        bsMonth = parts.monthIndex + 1
        bsDay = String(parts.day)
    }

    function updateFromBs(emitChange = false) {
        const year = Number.parseInt(normalizeNumberInput(bsYear), 10)
        const day = Number.parseInt(normalizeNumberInput(bsDay), 10)
        const month = Number(bsMonth)
        if (!year || !day || !month) return

        const maxDay = getBsMonthLength(year, month)
        const safeDay = Math.min(Math.max(day, 1), maxDay)
        if (safeDay !== day) bsDay = String(safeDay)

        const iso = toIsoDate(bsToGregorian(year, month, safeDay))
        value = iso
        dispatch("input", iso)
        if (emitChange) dispatch("change", iso)
    }

    function handleYearInput(event: Event) {
        bsYear = normalizeNumberInput((event.currentTarget as HTMLInputElement)?.value || "")
        updateFromBs()
    }

    function handleMonthChange(event: Event) {
        bsMonth = Number((event.currentTarget as HTMLSelectElement)?.value || bsMonth)
        updateFromBs(true)
    }

    function handleDayInput(event: Event) {
        bsDay = normalizeNumberInput((event.currentTarget as HTMLInputElement)?.value || "")
        updateFromBs()
    }
</script>

{#if displayMode === "ad"}
    <MaterialTimePicker {label} {value} {defaultValue} {id} {title} {disabled} {autofocus} style={$$props.style} on:input on:change isDate />
{:else}
    <div class="textfield filled bs-date-picker" class:disabled data-title={translateText(title)} style={$$props.style || null}>
        <div class="background" />

        <div class="bs-row">
            <input
                class="bs-part edit"
                class:disabled
                value={toNepaliDigits(bsYear || "")}
                inputmode="numeric"
                maxlength="4"
                placeholder={toNepaliDigits("2082")}
                {id}
                {disabled}
                {autofocus}
                on:input={handleYearInput}
                on:change={() => updateFromBs(true)}
            />

            <select
                class="bs-part month edit"
                class:disabled
                {disabled}
                value={bsMonth}
                on:change={handleMonthChange}
            >
                {#each BS_MONTHS as monthName, index}
                    <option value={index + 1}>{toNepaliDigits(index + 1)} {monthName}</option>
                {/each}
            </select>

            <input
                class="bs-part edit"
                class:disabled
                value={toNepaliDigits(bsDay || "")}
                inputmode="numeric"
                maxlength="2"
                placeholder={toNepaliDigits("1")}
                {disabled}
                on:input={handleDayInput}
                on:change={() => updateFromBs(true)}
            />
        </div>

        <label for={id}>{@html translateText(label, $dictionary)} (BS)</label>
        <span class="gregorian">AD: {value || "----"}</span>
        <span class="underline" />
    </div>
{/if}

<style>
    .bs-date-picker {
        position: relative;
        width: 100%;
        color: var(--text);
        border-bottom: 1.2px solid var(--primary-lighter);
        height: 50px;
    }

    .background {
        position: absolute;
        inset: 0;
        background-color: var(--primary-darkest);
        border-radius: 4px 4px 0 0;
        z-index: 0;
    }

    .bs-row {
        position: relative;
        z-index: 1;
        display: grid;
        grid-template-columns: 82px 1fr 52px;
        align-items: center;
        gap: 0.35rem;
        padding: 1.1rem 0.75rem 0.2rem;
    }

    .bs-part {
        width: 100%;
        border: none;
        outline: none;
        background-color: transparent;
        color: var(--text);
        font: inherit;
        font-size: 0.92rem;
        border-radius: 4px;
        padding: 0.2rem 0.3rem;
    }

    .bs-part.month {
        padding-inline: 0.4rem;
        color-scheme: dark;
        background-color: rgb(255 255 255 / 0.08);
    }

    .bs-part.month option {
        background-color: var(--primary-darkest);
        color: var(--text);
    }

    .bs-part:focus {
        background-color: var(--hover);
    }

    .bs-part.disabled {
        cursor: not-allowed;
        opacity: 0.4;
    }

    label {
        position: absolute;
        left: 0.75rem;
        top: 0.25rem;
        font-size: 0.75rem;
        color: var(--secondary);
        font-weight: 500;
        opacity: 1;
        pointer-events: none;
        z-index: 1;
    }

    .gregorian {
        position: absolute;
        right: 0.75rem;
        top: 0.18rem;
        font-size: 0.62rem;
        opacity: 0.6;
        z-index: 1;
    }

    .underline {
        position: absolute;
        bottom: 0;
        left: 0;
        height: 1.2px;
        width: 100%;
        background-color: var(--secondary);
        transform: scaleX(0);
        transition: transform 0.2s ease;
        transform-origin: left;
        z-index: 2;
    }

    .bs-date-picker:has(.bs-part:focus) .underline {
        transform: scaleX(1);
    }
</style>
