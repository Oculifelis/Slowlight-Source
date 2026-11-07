// Copied into run/kubejs/server_scripts/ by tools/run_client_test.sh. ServerChecks.kubejsChecks reads the result.
ServerEvents.recipes(event => {
  event.custom({
    type: 'slowlight:ageing',
    ingredient: { item: 'minecraft:rotten_flesh' },
    result: { id: 'minecraft:leather' },
    days: 3
  }).id('sltest:kjs_custom')
  event.recipes.slowlight.ageing('4x minecraft:bone_meal', 'minecraft:bone_block', 2).id('sltest:kjs_schema')
  event.remove({ type: 'slowlight:ageing', output: 'minecraft:blue_dye' })
})
