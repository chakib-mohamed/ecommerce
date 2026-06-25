/** Format a numeric amount as a whole-dollar price string, e.g. 89 → "$89". */
export const money = (value: number): string => '$' + Number(value).toFixed(0);
