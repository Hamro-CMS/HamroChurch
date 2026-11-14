<script lang="ts">
    import { onMount } from "svelte"
    import { Main } from "../../../../types/IPC/Main"
    import { requestMain, sendMain } from "../../../IPC/main"
    import { newToast } from "../../../utils/common"
    import Icon from "../../helpers/Icon.svelte"
    import InputRow from "../../input/InputRow.svelte"
    import MaterialButton from "../../inputs/MaterialButton.svelte"
    import MaterialToggleSwitch from "../../inputs/MaterialToggleSwitch.svelte"

    let loading = true
    let sharingEnabled = false
    let anonymousDeviceId = ""
    let consentUpdatedAt: string | null = null
    let deleting = false
    let statusNote = ""

    onMount(async () => {
        const privacy = await requestMain(Main.GET_PRIVACY_SETTINGS)
        if (privacy) {
            sharingEnabled = privacy.consent === true
            anonymousDeviceId = privacy.anonymousDeviceId || ""
            consentUpdatedAt = privacy.consentUpdatedAt || null
        }
        loading = false
    })

    async function handleToggle(e: CustomEvent<boolean>) {
        const nextConsent = e.detail === true
        sharingEnabled = nextConsent
        statusNote = ""

        const updated = await requestMain(Main.SET_ANALYTICS_CONSENT, { consent: nextConsent })
        if (updated) {
            sharingEnabled = updated.consent === true
            anonymousDeviceId = updated.anonymousDeviceId || anonymousDeviceId
            consentUpdatedAt = updated.consentUpdatedAt || null
        }

        newToast(
            sharingEnabled
                ? "Anonymous usage statistics enabled."
                : "Anonymous usage statistics turned off. All background pings stopped."
        )
    }

    async function copyDeviceId() {
        if (!anonymousDeviceId) return
        try {
            await navigator.clipboard.writeText(anonymousDeviceId)
            newToast("Anonymous Device ID copied to clipboard.")
        } catch {
            newToast("Could not copy ID to clipboard.")
        }
    }

    async function deleteServerLogs() {
        if (deleting || !anonymousDeviceId) return
        deleting = true
        statusNote = ""

        try {
            const result = await requestMain(Main.DELETE_ANALYTICS_DATA)
            if (result?.success) {
                anonymousDeviceId = result.anonymousDeviceId || anonymousDeviceId
                const count = typeof result.deleted === "number" ? result.deleted : 0
                statusNote =
                    count > 0
                        ? `Deleted ${count} anonymous server record(s) and generated a new local Anonymous Device ID.`
                        : "Verified zero server logs remain for your device and generated a new local Anonymous Device ID."
                newToast("Anonymous server logs deleted.")
            } else {
                statusNote = `Could not reach server (${result?.error || "network error"}). Please try again or email support@hamrocms.com.`
                newToast("Could not delete server logs right now.")
            }
        } finally {
            deleting = false
        }
    }

    function contactPrivacySupport() {
        const subject = encodeURIComponent("Hamro Church Privacy / Anonymous Data Deletion")
        const body = encodeURIComponent(
            `Hello Hamro Church Support,\n\nPlease ensure any anonymous usage records associated with my Anonymous Device ID are deleted:\nAnonymous Device ID: ${anonymousDeviceId}\n`
        )
        sendMain(Main.URL, `mailto:support@hamrocms.com?subject=${subject}&body=${body}`)
    }
</script>

<div class="privacy-settings">
    <div class="status-banner" class:active={sharingEnabled}>
        <div class="status-title">
            <Icon id="protected" white />
            <span>
                {#if loading}
                    Checking privacy preferences...
                {:else if sharingEnabled}
                    Anonymous Usage Statistics: Enabled
                {:else}
                    Anonymous Usage Statistics: Off (Zero Telemetry Sent)
                {/if}
                {#if consentUpdatedAt}
                    <small class="updated-meta">({new Date(consentUpdatedAt).toLocaleString()})</small>
                {/if}
            </span>
        </div>
        <p class="status-desc">
            All presentations, hymns, Bibles, and church records remain <strong>100% local and offline</strong> on your
            computer. We <strong>NEVER</strong> collect your name, IP address, computer hostname, system username, or
            any church/member data.
        </p>
    </div>

    <MaterialToggleSwitch
        label="Share Anonymous Usage Statistics (OS, City/Country, Duration)"
        checked={sharingEnabled}
        defaultValue={false}
        disabled={loading}
        on:change={handleToggle}
    />

    <div class="card">
        <h3>Anonymous Device ID</h3>
        <p class="helper">
            A random local UUID v4 used solely to prevent duplicate device counting when anonymous statistics are
            enabled. It contains zero hardware or personal identifiers.
        </p>

        <div class="device-id-row">
            <code class="device-id-code">{anonymousDeviceId || "Generating..."}</code>
            <MaterialButton variant="outlined" icon="copy" disabled={!anonymousDeviceId} on:click={copyDeviceId}>
                Copy ID
            </MaterialButton>
        </div>
    </div>

    <div class="card">
        <h3>Server Logs &amp; Data Deletion</h3>
        <p class="helper">
            You can permanently erase any anonymous usage statistics associated with your local Anonymous Device ID at
            any time, or contact privacy support at <strong>support@hamrocms.com</strong>.
        </p>

        <InputRow>
            <MaterialButton
                variant="outlined"
                style="width: 100%; justify-content: left;"
                icon="delete"
                disabled={deleting || !anonymousDeviceId}
                on:click={deleteServerLogs}
            >
                {deleting ? "Deleting Anonymous Server Logs..." : "Delete Server Logs for This Anonymous Device ID"}
            </MaterialButton>
        </InputRow>

        <InputRow>
            <MaterialButton
                variant="outlined"
                style="width: 100%; justify-content: left;"
                icon="launch"
                on:click={contactPrivacySupport}
            >
                Contact Privacy Support (support@hamrocms.com)
            </MaterialButton>
        </InputRow>

        {#if statusNote}
            <p class="status-note">{statusNote}</p>
        {/if}
    </div>
</div>

<style>
    .privacy-settings {
        display: flex;
        flex-direction: column;
        gap: 16px;
        max-width: 780px;
    }

    .status-banner {
        background: color-mix(in srgb, var(--primary-darkest) 85%, black 15%);
        border: 1px solid var(--primary-lighter);
        border-radius: 10px;
        padding: 14px 16px;
    }

    .status-banner.active {
        border-color: var(--secondary);
    }

    .status-title {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 600;
        margin-bottom: 6px;
    }

    .updated-meta {
        font-weight: 400;
        opacity: 0.65;
        margin-left: 6px;
        font-size: 0.85em;
    }

    .status-desc {
        margin: 0;
        font-size: 0.9em;
        line-height: 1.55;
        opacity: 0.86;
    }

    .card {
        background: color-mix(in srgb, var(--primary-darkest) 85%, black 15%);
        border: 1px solid var(--primary-lighter);
        border-radius: 10px;
        padding: 14px 16px;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    .card h3 {
        margin: 0;
        font-size: 1.02em;
        color: var(--text);
    }

    .helper {
        margin: 0;
        font-size: 0.88em;
        line-height: 1.5;
        opacity: 0.8;
    }

    .device-id-row {
        display: flex;
        align-items: center;
        gap: 10px;
        flex-wrap: wrap;
    }

    .device-id-code {
        flex: 1;
        min-width: 240px;
        padding: 9px 12px;
        border-radius: 6px;
        background: rgb(0 0 0 / 0.35);
        border: 1px solid var(--primary-lighter);
        font-family: Consolas, "Courier New", monospace;
        font-size: 0.9em;
        user-select: all;
        word-break: break-all;
    }

    .status-note {
        margin: 4px 0 0;
        font-size: 0.88em;
        color: var(--secondary);
    }
</style>
