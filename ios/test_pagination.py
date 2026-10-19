#!/usr/bin/env python3
"""Exercise the generated native result loop with deterministic result batches.

This executes the emitted control flow, not Apple's Shortcuts runtime.
"""
from build_shortcut import build


def browse(workflow, records, clicks):
    actions = workflow['WFWorkflowActions']
    start = next(i for i, a in enumerate(actions) if a['WFWorkflowActionIdentifier'].endswith('setvariable') and a['WFWorkflowActionParameters'].get('WFVariableName') == '显示结果') - 1
    # Stop at the end of the outer source loop. Only the result UI is simulated.
    end = max(i for i, a in enumerate(actions) if a['WFWorkflowActionIdentifier'].endswith('repeat.each'))
    variables = {'搜索结果': records, '页码': '1'}
    outputs = {}; stack = []; screens = []; decisions = iter(clicks)
    def value(v):
        if isinstance(v, dict):
            if v.get('WFSerializationType') in ['WFTextTokenAttachment', 'WFTextTokenString']:
                return value(v['Value'])
            if v.get('Type') == 'Variable':
                if 'Variable' in v:return value(v['Variable'])
                return variables[v['VariableName']]
            if v.get('Type') == 'ActionOutput':return outputs[v['OutputUUID']]
            if 'string' in v:
                text = v['string']
                for _, ref in sorted(v['attachmentsByRange'].items()):text = text.replace('\ufffc', str(value(ref)), 1)
                return text
        return v
    def closing(index):
        group = actions[index]['WFWorkflowActionParameters']['GroupingIdentifier']
        return next(j for j in range(index + 1, len(actions)) if actions[j]['WFWorkflowActionParameters'].get('GroupingIdentifier') == group and actions[j]['WFWorkflowActionParameters'].get('WFControlFlowMode') == 2)
    i = start
    while i < end:
        a = actions[i]; p = a['WFWorkflowActionParameters']; kind = a['WFWorkflowActionIdentifier'].split('is.workflow.actions.')[1]; result = None
        if kind == 'list':result = []
        elif kind == 'setvariable':variables[p['WFVariableName']] = value(p['WFInput'])
        elif kind == 'appendvariable':variables[p['WFVariableName']].append(value(p['WFInput']))
        elif kind == 'gettext':result = value(p['WFTextActionText'])
        elif kind == 'count':result = len(value(p['WFInput']))
        elif kind == 'conditional':
            if p['WFControlFlowMode'] == 0:
                lhs = value(p['WFInput']); rhs = p.get('WFNumberValue', p.get('WFConditionalActionString')); code = p['WFCondition']
                ok = {0: lambda: lhs < int(rhs), 2: lambda: lhs > int(rhs), 5: lambda: str(lhs) != str(rhs)}[code]()
                if not ok:i = closing(i)
        elif kind == 'repeat.each':
            if p['WFControlFlowMode'] == 0:
                items = value(p['WFInput']); last = closing(i)
                if not items:i = last
                else:
                    stack.append([i, last, items, 0]);variables['Repeat Item 2'] = items[0]
            else:
                loop = stack[-1];loop[3] += 1
                if loop[3] < len(loop[2]):variables['Repeat Item 2'] = loop[2][loop[3]];i = loop[0]
                else:stack.pop()
        elif kind == 'choosefromlist':
            screen = list(value(p['WFInput']));screens.append(screen)
            try:decision = next(decisions)
            except StopIteration:return screens
            if decision == 'cancel':return screens
            assert decision == 'next';result = screen[-1];assert result == '下一页 ▶'
        else:
            raise AssertionError('Unexpected action in navigation simulation: ' + kind)
        outputs[p['UUID']] = result;i += 1
    return screens


if __name__ == '__main__':
    for variant in ['standard', 'adult']:
        workflow = build(variant)
        for count in [1, 10, 15, 16, 30, 31, 100]:
            records = ['resource-' + str(i) for i in range(count)]
            screens = browse(workflow, records, ['next'] * 20)
            assert all(1 <= len(s) - 1 <= 15 and s[-1] == '下一页 ▶' for s in screens)
            assert [item for screen in screens for item in screen[:-1]] == records
            first = browse(workflow, records, ['cancel'])
            assert len(first) == 1 and first[0][:-1] == records[:15]
    print('Pagination checks passed: both variants; 1/10/15/16/30/31/100 results; order, no loss, no duplicates, cancel.')
