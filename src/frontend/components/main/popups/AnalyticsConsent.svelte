<script lang="ts">
    import { Main } from "../../../../types/IPC/Main"
    import { requestMain } from "../../../IPC/main"
    import { activePopup } from "../../../stores"
    import MaterialButton from "../../inputs/MaterialButton.svelte"

    let submitting = false

    async function chooseConsent(consent: boolean) {
        if (submitting) return
        submitting = true
        try {
            await requestMain(Main.SET_ANALYTICS_CONSENT, { consent })
        } finally {
            submitting = false
            activePopup.set(null)
        }
    }
</script>

<div class="consent-popup">
    <p class="consent-message">
        To help improve <strong>Hamro Church</strong>, we only collect basic, anonymous statistics: your
        <strong>Country/City, Operating System, and how long the app is used</strong>. We <strong>NEVER</strong> collect
        your name, IP address, computer name, or any church/member data. You can turn this off anytime in
        <strong>Settings &gt; Privacy</strong>.
    </p>

    <div class="consent-actions">
        <MaterialButton variant="outlined" disabled={submitting} icon="close" on:click={() => chooseConsent(false)}>
            Decline
        </MaterialButton>
        <MaterialButton variant="contained" disabled={submitting} icon="check" on:click={() => chooseConsent(true)}>
            Accept
        </MaterialButton>
    </div>
</div>

<style>
    .consent-popup {
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
        max-width: 560px;
    }

    .consent-message {
        margin: 0;
        font-size: 0.96rem;
        line-height: 1.65;
        color: var(--text);
        opacity: 0.94;
    }

    .consent-message strong {
        color: #ffffff;
        font-weight: 600;
    }

    .consent-actions {
        display: flex;
        justify-content: flex-end;
        align-items: center;
        gap: 0.75rem;
        padding-top: 0.25rem;
    }
</style>
