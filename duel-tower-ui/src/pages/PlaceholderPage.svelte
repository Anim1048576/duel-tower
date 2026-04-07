<script lang="ts">
  type Props = {
    title: string
    description: string
  }

  let { title, description }: Props = $props()
</script>

<section class="placeholder-page">
  <h2>{title}</h2>
  <p>{description}</p>
</section>
