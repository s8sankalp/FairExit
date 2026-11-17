// Indian number format: 150000 -> ₹1,50,000
const rupees = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

export const money = (value) => (value === null || value === undefined ? '' : rupees.format(Number(value)));

export const date = (iso) =>
  iso ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';

export const dateTime = (iso) =>
  iso ? new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '';

/** NOT_IN_AGREEMENT -> Not in agreement */
export const words = (value) =>
  value ? value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ') : '';

export const CATEGORIES = ['PAINTING', 'CLEANING', 'REPAIR', 'UTILITY', 'RENT', 'SOCIETY_CHARGE', 'OTHER'];
export const FLAT_SIZES = ['PG_ROOM', 'ONE_RK', 'ONE_BHK', 'TWO_BHK', 'THREE_BHK', 'FOUR_BHK_PLUS'];
export const FLAT_LABELS = {
  PG_ROOM: 'PG room', ONE_RK: '1RK', ONE_BHK: '1BHK', TWO_BHK: '2BHK', THREE_BHK: '3BHK', FOUR_BHK_PLUS: '4BHK+',
};
export const STATES = [
  ['KA', 'Karnataka'], ['KL', 'Kerala'], ['AP', 'Andhra Pradesh'], ['AR', 'Arunachal Pradesh'], ['AS', 'Assam'],
  ['BR', 'Bihar'], ['CG', 'Chhattisgarh'], ['GA', 'Goa'], ['GJ', 'Gujarat'], ['HR', 'Haryana'],
  ['HP', 'Himachal Pradesh'], ['JH', 'Jharkhand'], ['MP', 'Madhya Pradesh'], ['MH', 'Maharashtra'],
  ['MN', 'Manipur'], ['ML', 'Meghalaya'], ['MZ', 'Mizoram'], ['NL', 'Nagaland'], ['OD', 'Odisha'],
  ['PB', 'Punjab'], ['RJ', 'Rajasthan'], ['SK', 'Sikkim'], ['TN', 'Tamil Nadu'], ['TG', 'Telangana'],
  ['TR', 'Tripura'], ['UP', 'Uttar Pradesh'], ['UK', 'Uttarakhand'], ['WB', 'West Bengal'],
  ['AN', 'Andaman and Nicobar Islands'], ['CH', 'Chandigarh'], ['DH', 'Dadra and Nagar Haveli and Daman and Diu'],
  ['DL', 'Delhi'], ['JK', 'Jammu and Kashmir'], ['LA', 'Ladakh'], ['LD', 'Lakshadweep'], ['PY', 'Puducherry'],
];
